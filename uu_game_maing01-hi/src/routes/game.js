//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent, useEffect, useRef, useState} from "uu5g04-hooks";
import Config from "./config/config";
import Calls from "../calls";
import Canvas from "../bricks/canvas";
import RenderHelper from "../bricks/render-helper";
import ShakeHelper from "../bricks/shake-helper";
//@@viewOff:imports

//@@viewOn:constants
const ASSETS = "../assets";

/**
 * Length of one server tick in milliseconds. The real rate is measured from the interval between
 * incoming GameEvents; this is the fallback used until at least two of them have arrived, and the
 * conversion used to turn tick counters (respawn) into seconds.
 */
const SERVER_TICK_MS = 50;

/**
 * The server keeps at most this many moves per player per tick, applies a single physics step per
 * tick and collapses horizontal input to the last direction it saw - everything above this is
 * discarded, so there is nothing to gain from sending more.
 */
const MAX_MOVES_PER_TICK = 4;

/**
 * Upper bound on the inputs kept for reconciliation - about a second and a half of play at the tick
 * rate, comfortably more than any sane round trip.
 *
 * The buffer is drained by acknowledgements, so it must not depend on getting them: a server that
 * stops answering, or one too old to publish `lastProcessedSeq` at all, would otherwise grow it for
 * as long as the page is open. Past this the oldest are dropped and the prediction simply falls back
 * towards the authoritative position.
 */
const MAX_UNACKED_MOVES = 32;

/**
 * How long to wait before asking the server for a full state again. Throttled so that a server
 * which for any reason does not send one degrades into a slow retry rather than a reconnect loop.
 */
const FULL_STATE_RETRY_MS = 3000;

/**
 * How long to wait before polling again after a failed poll, so a server that is down is retried at
 * a human pace rather than as fast as the network will refuse.
 */
const POLL_RETRY_MS = 1000;

const DEFAULT_AREA = {width: 1400, height: 600};

const IMAGES = {
  background: `${ASSETS}/background_clouds.png`,
  grave: `${ASSETS}/graveTest.svg`,
  ice: `${ASSETS}/iceBlock.png`,
  playerRight: `${ASSETS}/gun_idle/E_E_Gun__Idle_000.png`,
  playerLeft: `${ASSETS}/gun_idle/E_E_Gun__Idle_000_left.png`,
};

const OBSTACLE_IMAGES = {
  TREE: `${ASSETS}/tree.png`,
  HOUSE: `${ASSETS}/house.png`,
  TAVERN: `${ASSETS}/tavern.png`,
  WOODEN_BOX: `${ASSETS}/wood_box.png`,
  METAL_BOX: `${ASSETS}/metal_box.png`,
  WALL: `${ASSETS}/wall2.png`,
  TROLL_FACE: `${ASSETS}/trollFace.png`,
};

const AMMO_IMAGES = {
  Bullet: `${ASSETS}/Bullet.png`,
  Mine: `${ASSETS}/Bomb.png`,
};

const BONUS_IMAGES = {
  ImmortalitySpecialAbility: `${ASSETS}/bonus_imortal2.png`,
  IncreasedSpeedSpecialAbility: `${ASSETS}/bonus_speed.png`,
  FreezeOpponentsSpecialAbility: `${ASSETS}/bonus_freeze.jpg`,
  IncreaseLiveSpecialAbility: `${ASSETS}/bonus_life.png`,
};

const SOUNDS = {
  theme: `${ASSETS}/theme.mp3`,
  fired: `${ASSETS}/fired.mp3`,
  minePlanted: `${ASSETS}/bomb-has-been-planted-sound-effect-cs-go.mp3`,
};

const EXPLOSION_SOUNDS = [1, 2, 3].map((index) => `${ASSETS}/explosions_sound/${index}.wav`);
const DEATH_SOUNDS = Array.from({length: 15}, (value, index) => `${ASSETS}/death_sound/${index + 1}.wav`);

const DEG_TO_RAD = Math.PI / 180;
//@@viewOff:constants

//@@viewOn:helpers
// Every sprite and sound used to be re-created inside the draw loop - a `new Image()` per obstacle,
// per player, per bullet and per bonus item on every single frame, plus a `new Audio()` per sound
// event. At ~30 frames per second that is thousands of elements a second, none of which ever had
// time to decode before it was thrown away. They are created once here and reused instead.
const imageRegistry = new Map();
const audioRegistry = new Map();

function getImage(src) {
  let image = imageRegistry.get(src);
  if (!image) {
    image = new Image();
    image.src = src;
    imageRegistry.set(src, image);
  }
  return image;
}

function getAudio(src) {
  let audio = audioRegistry.get(src);
  if (!audio) {
    audio = new Audio(src);
    audio.preload = "auto";
    audioRegistry.set(src, audio);
  }
  return audio;
}

/**
 * Draws a preloaded sprite. An image that has not decoded yet is skipped entirely (drawing it
 * would paint nothing anyway, and throws in some browsers).
 *
 * @returns {boolean} whether anything was painted
 */
function drawSprite(ctx, src, x, y, width, height) {
  const image = getImage(src);
  if (!image.complete || !image.naturalWidth) {
    return false;
  }
  ctx.drawImage(image, x, y, width, height);
  return true;
}

function playSound(src) {
  const audio = getAudio(src);
  try {
    audio.currentTime = 0;
  } catch (error) {
    // Safari throws while the media is still loading; the sound simply plays from where it is.
  }
  const played = audio.play();
  // play() is rejected until the user has interacted with the page - that must never break a frame.
  if (played && typeof played.catch === "function") {
    played.catch(() => undefined);
  }
}

let assetsPreloaded = false;

/** Warms the registries. Idempotent, so the whole set is fetched at most once per page load. */
function preloadAssets() {
  if (assetsPreloaded) {
    return;
  }
  assetsPreloaded = true;
  [
    ...Object.values(IMAGES),
    ...Object.values(OBSTACLE_IMAGES),
    ...Object.values(AMMO_IMAGES),
    ...Object.values(BONUS_IMAGES),
  ].forEach(getImage);
  [...Object.values(SOUNDS), ...EXPLOSION_SOUNDS, ...DEATH_SOUNDS].forEach(getAudio);
}

const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

const pickRandom = (list) => list[Math.floor(Math.random() * list.length)];

/**
 * Every solid box in the level, flattened out of the obstacles the setup arrived with.
 *
 * <p>Cached on the setup object itself, because the walls are wanted on every rendered frame and
 * the level they come from cannot change for the whole match.
 */
const wallsCache = new WeakMap();

const EMPTY_WALLS = [];

function getWalls(setup) {
  const obstacles = setup?.obstacles;
  if (!obstacles) {
    return EMPTY_WALLS;
  }
  let walls = wallsCache.get(setup);
  if (!walls) {
    walls = [];
    for (const obstacle of obstacles) {
      for (const wall of obstacle.walls ?? []) {
        walls.push(wall);
      }
    }
    wallsCache.set(setup, walls);
  }
  return walls;
}

/**
 * How far a body really gets when it moves, given what is in the way.
 *
 * <p>The client draws ahead of the server - it has to, or holding a key would only move you a
 * network round trip later - and drawing ahead means predicting, which until now meant predicting
 * that walls are not there. Walking into a crate, the prediction pushed a step into it, the next
 * state from the server put the player back flush against it, and the next frame pushed in again:
 * the player flickered back and forth against every solid in the level, twenty times a second.
 *
 * <p>Same rule as the server's collision space: travel as far as possible and stop flush against the
 * first blocker, treating a touch as clear the way {@code GameRectangle.intersects} does.
 *
 * @return the x the body ends up at
 */
function slideX(body, dx, walls) {
  let limit = body.x + dx;
  if (!dx) {
    return limit;
  }
  for (const wall of walls) {
    if (body.y + body.height <= wall.y || body.y >= wall.y + wall.height) {
      // Passing above or below it.
      continue;
    }
    if (dx > 0 && body.x + body.width <= wall.x) {
      limit = Math.min(limit, wall.x - body.width);
    } else if (dx < 0 && body.x >= wall.x + wall.width) {
      limit = Math.max(limit, wall.x + wall.width);
    }
  }
  return limit;
}

/** The same, vertically: what a fall or a jump drawn part way through a tick actually clears. */
function slideY(body, dy, walls) {
  let limit = body.y + dy;
  if (!dy) {
    return limit;
  }
  for (const wall of walls) {
    if (body.x + body.width <= wall.x || body.x >= wall.x + wall.width) {
      continue;
    }
    if (dy > 0 && body.y + body.height <= wall.y) {
      limit = Math.min(limit, wall.y - body.height);
    } else if (dy < 0 && body.y >= wall.y + wall.height) {
      limit = Math.max(limit, wall.y + wall.height);
    }
  }
  return limit;
}

/** Degrees, 0 = right and 90 = down - the convention the server expects for firedAngle. */
function getAngle(cx, cy, ex, ey) {
  return Math.atan2(ey - cy, ex - cx) * (180 / Math.PI);
}

function getCurrentPlayerId() {
  const session = UU5.Environment.getSession && UU5.Environment.getSession();
  const identity = session && session.getIdentity && session.getIdentity();
  return identity ? identity.uuIdentity : null;
}

/**
 * The arena comes from the match setup, which the server sends once - with the level geometry and
 * the match settings - instead of repeating it in every tick.
 */
function getArea(setup) {
  return {
    width: setup?.params?.areaWidth ?? setup?.area?.width ?? DEFAULT_AREA.width,
    height: setup?.params?.areaHeight ?? setup?.area?.height ?? DEFAULT_AREA.height,
  };
}

/**
 * Player ids are the plain map keys now. They used to be double encoded by the server, which is
 * why every lookup was written as players[JSON.stringify(uuIdentity)].
 */
function getPlayer(output, playerId) {
  return playerId ? output?.game?.players?.[playerId] : undefined;
}

/**
 * Where an event happened. Events carry the kind of thing, its id where it has one, and the point
 * an effect belongs on - never the entity itself, which is in the same payload already.
 */
function getEventPoint(event) {
  return typeof event?.x === "number" && typeof event?.y === "number" ? event : null;
}
//@@viewOff:helpers

const Game = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Game",
  //@@viewOff:statics

  render: function (props) {
    //@@viewOn:hooks
    const [roomState, setRoomState] = useState();
    const [gameState, setGameState] = useState();
    // The static half of the protocol: arena, level geometry and match settings. It arrives once,
    // in a full state, and has to be held here because every tick after it leaves it out.
    const [setup, setSetup] = useState(null);
    const [waiting, setWaiting] = useState(false);
    const [connectionError, setConnectionError] = useState(null);
    const [showDebug, setShowDebug] = useState(false);

    const canvasRef = useRef();

    // All of the state the animation loop and the document level listeners need lives in refs. It
    // used to live in module level variables, which meant it survived unmounting and was shared by
    // every instance of the component.
    const gameStateRef = useRef(null);
    const setupRef = useRef(null);
    const previousPlayersRef = useRef(null);
    const stateReceivedAtRef = useRef(0);
    const tickDurationRef = useRef(SERVER_TICK_MS);
    const processedTickRef = useRef(null);
    const heldDirectionRef = useRef(null);
    const pendingMovesRef = useRef([]);
    const aimAngleRef = useRef(0);

    // Client side prediction with server reconciliation. Every move sent carries a sequence number
    // and is kept here until the server says it has dealt with it; what is left is exactly the input
    // that the authoritative position does not contain yet, and therefore what has to be replayed on
    // top of it. `inputBatch` groups the moves flushed together, because that is the unit the server
    // applies - see reconcileLocalPlayer.
    const moveSeqRef = useRef(0);
    const inputBatchRef = useRef(0);
    const unackedMovesRef = useRef([]);
    const themeMusicPlaysRef = useRef(false);

    // RenderHelper installs its own requestAnimationFrame loop the moment it is called, so calling
    // it straight in the render body (~20x per second here) leaked one loop per render.
    const helpersRef = useRef(null);
    if (!helpersRef.current) {
      helpersRef.current = {...RenderHelper(), ...ShakeHelper()};
    }
    const {addExplosion, shake} = helpersRef.current;

    // Deferred to the first mount of the game route: the module is imported eagerly by the SPA, so
    // preloading at module level would fetch the whole sprite and sound set on every page load.
    useEffect(preloadAssets, []);

    useEffect(() => {
      const roomId = props?.params?.roomId;
      if (!roomId) {
        return undefined;
      }

      let active = true;
      let eventSource;
      let fullStateRequestedAt = 0;

      /**
       * Whether this client has given up on the stream and is long-polling instead.
       *
       * `EventSource` cannot send an `Authorization` header, so wherever the server has no cookie
       * session to fall back on - a plain local server, with no gateway in front of it - a
       * non-public `sse` can only ever answer 403. `polling/poll` carries the identical payload over
       * an ordinary call, which does authenticate, so the game stays playable there.
       */
      let polling = false;
      /** Set whenever the next poll has to return the whole state rather than the next change. */
      let pollFull = true;

      /**
       * Gets a complete state back. The server hands one to every client that connects, so
       * reconnecting is the request - there is no separate endpoint for it. This is how a client
       * that has only ever seen deltas, after a reload or a dropped connection mid-match, recovers
       * the level it cannot draw without.
       */
      function requestFullState() {
        const now = Date.now();
        if (now - fullStateRequestedAt < FULL_STATE_RETRY_MS) {
          return;
        }
        fullStateRequestedAt = now;
        if (polling) {
          // The loop is already running; it just has to ask for everything on its next turn. There
          // is no connection to re-open, which is what makes the request in the polling case.
          pollFull = true;
          return;
        }
        eventSource && eventSource.close();
        subscribe();
      }

      function handleGameEvent(result) {
        const output = result.output;
        const previous = gameStateRef.current;
        const now = Date.now();

        if (output?.full) {
          // A full payload says so explicitly rather than leaving the client to guess from which
          // fields happen to be present.
          setupRef.current = output.setup ?? null;
          setSetup(output.setup ?? null);
          // A full state is "start your picture again from here" - it is what a new match begins
          // with, and the server numbers input per match. Replaying moves from the previous one on
          // top of the new positions would have the player start the round several steps off.
          unackedMovesRef.current = [];
        } else if (!setupRef.current && output?.game) {
          // A tick for a match whose level this client has never been sent - a reload or a dropped
          // connection halfway through. There is nothing to merge the delta into, so ask again.
          requestFullState();
        }

        if (previous && stateReceivedAtRef.current) {
          // Measured rather than hardcoded, and divided by the number of ticks that actually
          // elapsed so a dropped packet does not halve the apparent tick rate.
          const ticks = Math.max(1, (output?.tick ?? 0) - (previous?.tick ?? 0));
          const measured = (now - stateReceivedAtRef.current) / ticks;
          if (measured > 5 && measured < 1000) {
            tickDurationRef.current = tickDurationRef.current * 0.8 + measured * 0.2;
          }
        }

        previousPlayersRef.current = previous?.game?.players ?? null;
        gameStateRef.current = output;
        stateReceivedAtRef.current = now;
        setGameState(result);

        // Everything at or below the watermark is already in the positions above; whatever is left
        // over is what this client still has to replay on top of them.
        acknowledgeMoves(output);

        // Input is flushed once per received tick, which keeps it naturally in step with the
        // simulation without a second timer.
        flushMoves(roomId);
      }

      /** Both channels deliver the same envelope, so they share the one handler. */
      function handleEvent(result) {
        if (result.eventType === "RoomEvent") {
          setRoomState((oldValue) => ({...oldValue, ...result}));
        } else if (result.eventType === "GameEvent") {
          handleGameEvent(result);
        }
      }

      /**
       * Forgets what this client thought was in flight.
       *
       * Nothing sent over a connection that just died can be reasoned about any more: the first
       * state received afterwards carries the watermark it has to start from.
       */
      function resetInFlightMoves() {
        pendingMovesRef.current = [];
        unackedMovesRef.current = [];
      }

      function subscribe() {
        eventSource = new EventSource(`${Calls.getCommandUri("sse")}?roomId=${roomId}`);

        eventSource.onmessage = (event) => {
          if (!active) {
            return;
          }
          handleEvent(JSON.parse(event.data));
        };

        eventSource.onopen = () => {
          if (!active) {
            return;
          }
          setConnectionError(null);
          resetInFlightMoves();
        };

        eventSource.onerror = () => {
          if (!active) {
            return;
          }
          // `EventSource` reports a refused stream and a dropped one the same way, and it retries by
          // itself either way. Against a server that will never accept the stream at all that retry
          // is an endless loop, so the first failure is taken as the answer and the client moves to
          // the channel that can authenticate.
          startPolling();
        };
      }

      function startPolling() {
        if (polling) {
          return;
        }
        polling = true;
        if (eventSource) {
          eventSource.close();
          eventSource = undefined;
        }
        pollFull = true;
        resetInFlightMoves();
        pollLoop();
      }

      async function pollLoop() {
        while (active && polling) {
          try {
            const result = await Calls.poll({roomId, full: pollFull});
            if (!active) {
              return;
            }
            // Only once it has actually arrived - a poll that fails must ask again.
            pollFull = false;
            setConnectionError(null);
            // A poll that waited out its timeout without anything happening comes back empty; that
            // is the quiet case, not an error, and it simply asks again.
            if (result && result.eventType) {
              handleEvent(result);
            }
          } catch (error) {
            if (!active) {
              return;
            }
            setConnectionError("Connection to the game server was interrupted, reconnecting...");
            // Whatever was missed while the call was failing cannot be reconstructed from deltas.
            pollFull = true;
            resetInFlightMoves();
            await new Promise((resolve) => setTimeout(resolve, POLL_RETRY_MS));
          }
        }
      }

      async function joinAndSubscribe() {
        setWaiting(true);
        try {
          const room = await Calls.roomJoin({roomId});
          if (!active) {
            return;
          }
          // roomJoin returns the room itself while the SSE RoomEvent wraps it in `output`;
          // normalising here keeps a single shape for the whole component.
          setRoomState((oldValue) => ({...oldValue, output: room}));
          subscribe();
        } catch (error) {
          if (active) {
            setConnectionError("Unable to join the room.");
          }
        } finally {
          if (active) {
            setWaiting(false);
          }
        }
      }

      document.addEventListener("keydown", handleKeyDown);
      document.addEventListener("keyup", handleKeyUp);
      document.addEventListener("mousemove", handleMouseMove);
      document.addEventListener("mousedown", handleMouseDown);

      joinAndSubscribe();

      return () => {
        active = false;
        polling = false;
        eventSource && eventSource.close();
        // These used to be registered on document.body but removed from document, so every visit to
        // a room left another complete set of handlers behind.
        document.removeEventListener("keydown", handleKeyDown);
        document.removeEventListener("keyup", handleKeyUp);
        document.removeEventListener("mousemove", handleMouseMove);
        document.removeEventListener("mousedown", handleMouseDown);
        getAudio(SOUNDS.theme).pause();
        themeMusicPlaysRef.current = false;
        gameStateRef.current = null;
        setupRef.current = null;
        previousPlayersRef.current = null;
        processedTickRef.current = null;
        heldDirectionRef.current = null;
        pendingMovesRef.current = [];
        unackedMovesRef.current = [];
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [props?.params?.roomId]);

    useEffect(() => {
      if (roomState?.output?.state !== "ACTIVE" || themeMusicPlaysRef.current) {
        return;
      }
      const theme = getAudio(SOUNDS.theme);
      theme.loop = true;
      theme.volume = 0.2;
      playSound(SOUNDS.theme);
      themeMusicPlaysRef.current = true;
    }, [roomState?.output?.state]);
    //@@viewOff:hooks

    //@@viewOn:private
    async function startGameHandler() {
      try {
        await Calls.gameInstanceStartGame({roomId: props.params.roomId});
      } catch (error) {
        setConnectionError("Unable to start the game.");
      }
    }

    function queueMove(move) {
      const pending = pendingMovesRef.current;
      pending.push(move);
      if (pending.length > MAX_MOVES_PER_TICK) {
        pending.splice(0, pending.length - MAX_MOVES_PER_TICK);
      }
    }

    /**
     * Sends everything queued since the last flush, numbered, and keeps a copy until the server
     * acknowledges it.
     *
     * One flush is one server tick's worth of input, so the moves sent together are recorded under
     * one batch number: the server applies all the moves a client sent for a tick and then steps the
     * physics exactly once, which is what reconcileLocalPlayer has to reproduce. The batch number
     * itself stays here - the server has no use for it.
     */
    function flushMoves(roomId) {
      const queued = pendingMovesRef.current;
      pendingMovesRef.current = [];
      if (heldDirectionRef.current) {
        queued.push({move: heldDirectionRef.current});
      }
      if (!queued.length || !roomId) {
        return;
      }

      // Numbered after the cap, so a sequence number only ever stands for a move that was sent.
      const moves = queued.slice(-MAX_MOVES_PER_TICK)
        .map((move) => ({...move, seq: ++moveSeqRef.current}));
      rememberUnacked(moves, ++inputBatchRef.current);

      Calls.gameInstanceAddPlayerMove({roomId, playerMoves: moves}).catch(() => undefined);
    }

    /** Keeps sent input for replay, within a fixed bound - see MAX_UNACKED_MOVES. */
    function rememberUnacked(moves, batch) {
      const unacked = unackedMovesRef.current;
      for (const move of moves) {
        unacked.push({seq: move.seq, move: move.move, batch});
      }
      if (unacked.length > MAX_UNACKED_MOVES) {
        unacked.splice(0, unacked.length - MAX_UNACKED_MOVES);
      }
    }

    /**
     * Forgets the input the server says it has settled, and keeps the rest to be replayed.
     *
     * The watermark covers moves the server threw away as well as ones it applied, so anything at or
     * below it is finished either way and must not be replayed again.
     *
     * It also seeds the counter. A reload starts numbering again from zero while the server still
     * remembers how far the previous instance of this page had got, and would reject everything the
     * new one sends as already settled; adopting the server's number keeps the sequence monotone
     * across a reconnect without the client having to detect that one happened.
     */
    function acknowledgeMoves(output) {
      const ack = output?.lastProcessedSeq?.[getCurrentPlayerId()];
      if (typeof ack !== "number") {
        return;
      }
      if (ack > moveSeqRef.current) {
        moveSeqRef.current = ack;
      }
      unackedMovesRef.current = unackedMovesRef.current.filter((entry) => entry.seq > ack);
    }

    function handleKeyDown(event) {
      switch (event.key) {
        case "ArrowLeft":
        case "a":
          heldDirectionRef.current = "LEFT";
          event.preventDefault();
          break;
        case "ArrowRight":
        case "d":
          heldDirectionRef.current = "RIGHT";
          event.preventDefault();
          break;
        case "ArrowUp":
        case "w":
          queueMove({move: "UP"});
          event.preventDefault();
          break;
        default:
          break;
      }
    }

    function handleKeyUp(event) {
      switch (event.key) {
        case "ArrowLeft":
        case "a":
          // Only release the direction the key actually stands for, so holding both keys and
          // letting one go keeps walking the other way.
          if (heldDirectionRef.current === "LEFT") {
            heldDirectionRef.current = null;
          }
          break;
        case "ArrowRight":
        case "d":
          if (heldDirectionRef.current === "RIGHT") {
            heldDirectionRef.current = null;
          }
          break;
        default:
          break;
      }
    }

    /**
     * The canvas bitmap is the playfield in full (params.areaWidth x params.areaHeight) but CSS
     * renders it at whatever width the card has, so client coordinates have to be scaled back into
     * playfield coordinates before they can be used for aiming.
     */
    function toPlayfieldCoordinates(event) {
      const canvas = canvasRef.current;
      if (!canvas) {
        return null;
      }
      const rect = canvas.getBoundingClientRect();
      if (!rect.width || !rect.height) {
        return null;
      }
      const area = getArea(setupRef.current);
      return {
        x: (event.clientX - rect.left) * (area.width / rect.width),
        y: (event.clientY - rect.top) * (area.height / rect.height),
      };
    }

    function handleMouseMove(event) {
      const player = getPlayer(gameStateRef.current, getCurrentPlayerId());
      const point = player && toPlayfieldCoordinates(event);
      if (!point) {
        return;
      }
      aimAngleRef.current = getAngle(
        player.x + player.width / 2,
        player.y + player.height / 2,
        point.x,
        point.y
      );
    }

    function handleMouseDown(event) {
      // Only shoot on a left click that lands inside the arena, otherwise pressing any button in
      // the page - or opening the context menu - fires as well.
      if (event.button !== 0 || event.target !== canvasRef.current) {
        return;
      }
      handleMouseMove(event);
      if (event.shiftKey) {
        return;
      }
      queueMove({fired: "BULLET", firedAngle: aimAngleRef.current}); // TODO - other projectile types
    }

    function drawObstacles(ctx, obstacles) {
      for (let i = 0; i < (obstacles?.length ?? 0); i++) {
        const obstacle = obstacles[i];
        const src = OBSTACLE_IMAGES[obstacle.type];
        for (const wall of obstacle.walls ?? []) {
          if (src) {
            drawSprite(ctx, src, wall.x, wall.y, wall.width, wall.height);
          } else {
            // MUSHROOM and CONCRETE_BLOCK have no sprite in the asset set, but they are solid and
            // therefore still have to be visible.
            ctx.fillStyle = "yellow";
            ctx.fillRect(wall.x, wall.y, wall.width, wall.height);
          }
        }
      }
    }

    function drawBonusItems(ctx, items, tickFraction) {
      for (let i = 0; i < (items?.length ?? 0); i++) {
        const item = items[i];
        const src = BONUS_IMAGES[item.specialAbilityName];
        if (src) {
          // Carried between states exactly like the players are. `velocityY` is pixels per server
          // tick and the server drops it once the crate settles, so a landed crate is drawn where
          // it is and only a falling one is advanced.
          const y = item.y + (item.velocityY ?? 0) * tickFraction;
          drawSprite(ctx, src, item.x, y, item.width, item.height);
        }
      }
    }

    function drawAmmo(ctx, ammoList, tickFraction) {
      for (let i = 0; i < (ammoList?.length ?? 0); i++) {
        const ammo = ammoList[i];
        const src = AMMO_IMAGES[ammo.type];
        if (ammo.used || !src) {
          continue;
        }
        // Bullets no longer carry `direction`/`bulletSpeed`: they travel along `angle` (degrees,
        // 0 = right) at `speed` pixels per server tick. Reading the old fields yielded NaN, which
        // is why bullets used to disappear between ticks.
        const distance = (ammo.speed ?? 0) * tickFraction;
        const radians = (ammo.angle ?? 0) * DEG_TO_RAD;
        drawSprite(
          ctx,
          src,
          ammo.x + distance * Math.cos(radians),
          ammo.y + distance * Math.sin(radians),
          ammo.width,
          ammo.height
        );
      }
    }

    /**
     * Where the local player really is: the authoritative position the server just sent, with every
     * input it has not acknowledged yet replayed on top of it.
     *
     * <p>This is the whole point of numbering the input. Before it, the local player was overwritten
     * wholesale by each state that arrived, so at any real latency pressing right moved the player
     * right and then snapped them back a moment later - the state in flight is always older than the
     * input this client has already applied to itself.
     *
     * <p>One step per batch rather than per move, because that is what the server does: it applies
     * every move sent for a tick and then steps the physics once, so a batch is worth a single step
     * however many moves it holds, and the last direction in it is the one that wins.
     *
     * <p>The step currently being composed - the key held down but not flushed yet - is weighted by
     * how far into the tick we are. That both keeps a keypress visible immediately and keeps the
     * motion continuous: by the moment the flush turns it into a full step above, it has grown to a
     * whole step and starts again from zero. Input that begins or ends part way through a tick is
     * the one case where this jumps, by at most the fraction of a single step still owed.
     *
     * <p>Horizontal only. Jumping and falling depend on the server's idea of what the player is
     * standing on, so vertical position is left to it.
     *
     * <p>Replayed against the level, not through it. A replayed step is a step the server will also
     * refuse when it gets there, and pretending otherwise is what made walking into a crate flicker:
     * the prediction stood inside the crate and every arriving state shoved it back out.
     */
    function reconcileLocalPlayer(player, area, tickFraction, walls) {
      const speed = player.speed ?? 0;
      const maxX = Math.max(0, area.width - player.width);
      let x = player.x;
      let direction = player.direction;

      const walk = (from, dx) =>
        clamp(slideX({x: from, y: player.y, width: player.width, height: player.height}, dx, walls), 0, maxX);

      let batch = null;
      let batchDirection = null;
      const stepBatch = () => {
        if (batchDirection) {
          x = walk(x, batchDirection === "LEFT" ? -speed : speed);
          direction = batchDirection;
        }
      };
      for (const entry of unackedMovesRef.current) {
        if (entry.batch !== batch) {
          stepBatch();
          batch = entry.batch;
          batchDirection = null;
        }
        if (entry.move === "LEFT" || entry.move === "RIGHT") {
          batchDirection = entry.move;
        }
      }
      stepBatch();

      const held = heldDirectionRef.current;
      if (held) {
        x = walk(x, (held === "LEFT" ? -speed : speed) * tickFraction);
        direction = held;
      }

      return {x, direction};
    }

    function drawPlayers(ctx, output, area, tickFraction, names, walls) {
      const players = output?.game?.players ?? {};
      const previousPlayers = previousPlayersRef.current;
      const localPlayerId = getCurrentPlayerId();

      for (const playerId of Object.keys(players)) {
        const player = players[playerId];
        const alive = player.lives > 0;

        let x = player.x;
        let y = player.y;
        let direction = player.direction;

        if (alive && !player.frozen) {
          // `speed` is pixels per server tick, not per rendered frame - scaling it by the fraction
          // of a tick that has elapsed is what the old code got wrong.
          const speed = player.speed ?? 0;
          if (playerId === localPlayerId) {
            // Prediction with reconciliation, and only here: this client has never seen anybody
            // else's keystrokes, so there is nothing to replay for them.
            const predicted = reconcileLocalPlayer(player, area, tickFraction, walls);
            x = predicted.x;
            direction = predicted.direction;
          } else {
            // Remote players: `speed` is only the maximum, so the actual horizontal velocity is
            // taken from how far they moved during the previous tick. Carried forward against the
            // level for the same reason the local player is: somebody who stopped at a crate this
            // tick was still moving last tick, and without this they are drawn a step inside it.
            const previous = previousPlayers?.[playerId];
            const velocityX = previous ? clamp(player.x - previous.x, -speed, speed) : 0;
            x = clamp(
              slideX({x, y, width: player.width, height: player.height}, velocityX * tickFraction, walls),
              0,
              area.width - player.width
            );
          }
          if (!player.onGround) {
            y = clamp(
              slideY({x, y, width: player.width, height: player.height}, (player.velocityY ?? 0) * tickFraction, walls),
              0,
              area.height - player.height
            );
          }
        }

        if (alive) {
          drawSprite(
            ctx,
            direction === "LEFT" ? IMAGES.playerLeft : IMAGES.playerRight,
            x,
            y,
            player.width,
            player.height
          );
        } else {
          drawSprite(ctx, IMAGES.grave, x, y + 2, player.width, player.height);
        }

        ctx.font = "13px Comic Sans MS";
        ctx.fillStyle = "red";
        ctx.textAlign = "center";
        ctx.fillText(names[playerId] ?? playerId, x + player.width / 2, y - player.height - 5);

        if (alive) {
          const barWidth = player.width + 20;
          const barX = x - 10;
          const barY = y - player.height + 5;
          // Taken from the player rather than params.initialLives, so the bar stays correct even if
          // a game ever varies the maximum per player.
          const maxLives = player.maxLives > 0 ? player.maxLives : 1;

          ctx.fillStyle = "rgba(255,0,0,0.6)";
          ctx.fillRect(barX, barY, barWidth, 10);
          ctx.fillStyle = player.invulnerable ? "rgba(255,215,0,1)" : "rgba(0,255,0,0.6)";
          ctx.fillRect(barX, barY, barWidth * clamp(player.lives / maxLives, 0, 1), 10);

          if (player.invulnerable) {
            // Invulnerability is a flag on the player now; it used to be encoded as lives = 999.
            ctx.strokeStyle = "rgba(255,215,0,0.9)";
            ctx.lineWidth = 2;
            ctx.strokeRect(x - 2, y - 2, player.width + 4, player.height + 4);
          }
        }

        if (player.frozen) {
          // `frozen` is a flag now; it used to be guessed from speed === 0, which also matched any
          // player who simply was not moving.
          ctx.globalAlpha = 0.65;
          drawSprite(ctx, IMAGES.ice, x - 13, y - 20, player.width + 20, player.height + 25);
          ctx.globalAlpha = 1;
        }

        if (!alive && player.respawnAtTick != null) {
          const remainingTicks = player.respawnAtTick - (output.tick ?? 0);
          if (remainingTicks > 0) {
            ctx.font = "16px Comic Sans MS";
            ctx.fillStyle = "white";
            ctx.fillText(
              `${Math.ceil((remainingTicks * SERVER_TICK_MS) / 1000)}`,
              x + player.width / 2,
              y - 10
            );
          }
        }
      }
    }

    /**
     * Plays the sounds and effects of one tick.
     *
     * An event is `{action, objectName, id?, x?, y?}`: what happened, to what kind of thing, which
     * one of them where it has an identity, and where on the map. It used to carry whole entities -
     * a death shipped the entire player, a hit shipped both the bullet and its victim - all of
     * which is in `game` in the same payload anyway.
     */
    function renderEvents(ctx, events) {
      for (let i = 0; i < (events?.length ?? 0); i++) {
        const event = events[i];
        const point = getEventPoint(event);

        if (event.objectName === "Bullet") {
          if (event.action === "used") {
            playSound(SOUNDS.fired);
            point && addExplosion(ctx, point.x, point.y);
          } else if (event.action === "fired") {
            playSound(SOUNDS.fired);
          }
        } else if (event.objectName === "Mine") {
          if (event.action === "used") {
            playSound(pickRandom(EXPLOSION_SOUNDS));
            point && addExplosion(ctx, point.x, point.y);
          } else if (event.action === "fired") {
            playSound(SOUNDS.minePlanted);
          }
        } else if (event.objectName === "SoldatPlayer") {
          if (event.action === "liveDecreased") {
            playSound(SOUNDS.fired);
            point && addExplosion(ctx, point.x, point.y);
          } else if (event.action === "death") {
            playSound(pickRandom(DEATH_SOUNDS));
            canvasRef.current && shake(canvasRef.current);
          }
        }
      }
    }

    const draw = (ctx, canvas) => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      drawSprite(ctx, IMAGES.background, 0, 0, canvas.width, canvas.height);

      const output = gameStateRef.current;
      if (!output?.game) {
        return;
      }

      // Held from the last full state; the ticks in between do not repeat it.
      const staticParts = setupRef.current;
      const area = getArea(staticParts);
      // How far into the current server tick we are, 0..1. Clamped so that a stalled connection
      // freezes the scene instead of extrapolating everything off the map.
      const tickFraction = clamp(
        (Date.now() - stateReceivedAtRef.current) / (tickDurationRef.current || SERVER_TICK_MS),
        0,
        1
      );

      const names = {};
      for (const player of output.players ?? []) {
        names[player.playerId] = player.playerName || player.playerId;
      }

      const walls = getWalls(staticParts);

      drawObstacles(ctx, staticParts?.obstacles);
      drawBonusItems(ctx, output.game.bonusItemList, tickFraction);
      drawPlayers(ctx, output, area, tickFraction, names, walls);
      drawAmmo(ctx, output.game.ammo, tickFraction);

      // Sounds and explosions belong to a tick, not to a frame. They used to be replayed whenever
      // frameCount happened to be 1, which only ever worked because the whole animation loop was
      // torn down and restarted on every server update.
      if (output.tick !== processedTickRef.current) {
        processedTickRef.current = output.tick;
        renderEvents(ctx, output.gameEvents);
      }
    };
    //@@viewOff:private

    //@@viewOn:render
    if (waiting) {
      return <UU5.Bricks.Loading/>;
    }

    const output = gameState?.output;
    const params = setup?.params;
    const area = getArea(setup);
    const localPlayerId = getCurrentPlayerId();
    const localPlayer = getPlayer(output, localPlayerId);
    const connectedPlayers = roomState?.output?.connectedPlayers ?? [];
    const scoreboard = [...(output?.players ?? [])].sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
    // Progress is the one match-wide number that changes, so it rides with the tick rather than
    // with the settings.
    const progress = clamp(output?.progress ?? 0, 0, 1);
    const respawnInTicks = localPlayer?.respawnAtTick != null
      ? localPlayer.respawnAtTick - (output?.tick ?? 0)
      : 0;

    return (
      <UU5.Bricks.Section level={1} header="Game"
                          className={UU5.Common.Css.css`padding: 16px`}>

        Connected players: {connectedPlayers.map((p) => p.playerName || p.playerId).join(", ")}

        {output?.state === "RUNNING" && scoreboard.length > 0 && (
          <UU5.Bricks.Row className={UU5.Common.Css.css`margin-bottom: 8px`}>
            {scoreboard.map((player) => (
              <UU5.Bricks.Column key={player.playerId} colWidth="xs-6 s-4 m-3">
                <div className={UU5.Common.Css.css`
                  display: flex;
                  justify-content: space-between;
                  padding: 4px 8px;
                  border-radius: 4px;
                  background: ${player.playerId === localPlayerId ? "#e3f2fd" : "#f5f5f5"};
                  opacity: ${player.state === "ACTIVE" ? 1 : 0.5};
                `}>
                  <span>{player.playerName || player.playerId}</span>
                  <strong>{player.score ?? 0}</strong>
                </div>
              </UU5.Bricks.Column>
            ))}
          </UU5.Bricks.Row>
        )}

        {output?.state === "RUNNING" && params && (
          <div className={UU5.Common.Css.css`margin-bottom: 8px`}>
            <div className={UU5.Common.Css.css`
              height: 8px;
              width: 100%;
              border-radius: 4px;
              background: #e0e0e0;
              overflow: hidden;
            `}>
              <div className={UU5.Common.Css.css`height: 100%; background: #1976d2;`}
                   style={{width: `${progress * 100}%`}}/>
            </div>
            <small>
              {params.gameMode === "TIME_LIMIT"
                ? `${Math.max(0, Math.ceil((params.gameModeSecondLimit ?? 0) * (1 - progress)))} s remaining`
                : `First to ${params.gameModeStopAtScore ?? "?"} points`}
            </small>
          </div>
        )}

        {output?.state === "RUNNING" && respawnInTicks > 0 && (
          <div className={UU5.Common.Css.css`
            margin-bottom: 8px;
            padding: 4px 8px;
            border-radius: 4px;
            background: #fff3e0;
          `}>
            {/* respawnAtTick is a tick counter, so it only becomes a duration via the tick length. */}
            {`Respawning in ${Math.ceil((respawnInTicks * SERVER_TICK_MS) / 1000)} s`}
          </div>
        )}

        <UU5.Bricks.Card className="uu5-common-padding-s" style={{
          border: "1px solid black",
          minHeight: "550px",
          width: "100%"
        }}>
          {output?.state === "RUNNING" && output?.tick > 0
            ?
            <Canvas draw={draw}
                    canvasRef={canvasRef}
                    width={area.width}
                    height={area.height}
            />
            :
            <>
              Connected players: {connectedPlayers.map((p) => p.playerName || p.playerId).join(", ")}
              {connectionError}

              <UU5.Bricks.Paragraph>
                {output?.state === "WAITING"
                  && "Waiting for next players. Move player with arrows or A W D, fire on mouse left click."}
                {output?.state === "FINISHED" && "Game is finished"}
              </UU5.Bricks.Paragraph>
              {output?.state === "FINISHED" && scoreboard.map((player) => (
                <UU5.Bricks.Paragraph key={player.playerId}>
                  {`${player.playerName || player.playerId} - ${player.score ?? 0}`}
                </UU5.Bricks.Paragraph>
              ))}
              <UU5.Bricks.Button onClick={startGameHandler}
                                 colorSchema={"primary"}>Start new
                game<UU5.Bricks.Icon
                  icon="mdi-apple"/></UU5.Bricks.Button>
            </>
          }

        </UU5.Bricks.Card>


        {output?.tick > 0 &&
          <UU5.Bricks.Button onClick={startGameHandler}
                             colorSchema={"secondary"}>Restart
            game<UU5.Bricks.Icon
              icon="mdi-apple"/></UU5.Bricks.Button>}

        {/* Serialising the whole game state into the DOM 20x per second is a real performance drag,
            so the debug panels are only mounted when they are asked for. */}
        <UU5.Bricks.Button onClick={() => setShowDebug((value) => !value)}
                           colorSchema={"grey"}>
          {showDebug ? "Hide debug" : "Show debug"}
        </UU5.Bricks.Button>

        {showDebug &&
          <>
            <UU5.Bricks.Panel header={props?.params?.roomId} content={<pre>
              RoomId :: {props?.params?.roomId}
            </pre>}/>

            <UU5.Bricks.Panel header="gameState debug" content={<pre>
              GAME: {JSON.stringify(gameState, undefined, 2)}
            </pre>}/>

            {/* The static half arrives once and is not in the tick payloads above. */}
            <UU5.Bricks.Panel header="setup debug" content={<pre>
              SETUP: {JSON.stringify(setup, undefined, 2)}
            </pre>}/>

            <UU5.Bricks.Panel header="roomState debug" content={<pre>
              ROOM: {JSON.stringify(roomState, undefined, 2)}
            </pre>}/>
          </>}

      </UU5.Bricks.Section>
    );
    //@@viewOff:render
  },
});

export default Game;
