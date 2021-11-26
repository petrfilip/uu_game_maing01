//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent, useEffect, useRef, useState} from "uu5g04-hooks";
import Config from "./config/config";
import Calls from "../calls";
import Canvas from "../bricks/canvas";
import RenderHelper from "../bricks/render-helper";
import ShakeHelper from "../bricks/shake-helper";
//@@viewOff:imports
const themeMusic = new Audio("../assets/theme.mp3");
let moveList = [];
let playerAnimation = [];
let gameUpdateRate = new Date().getTime();
let lastServerUpdateTimestamp = new Date().getTime();
let currentServerUpdateTimestamp = new Date().getTime();

let angle = 0;

let moving = false;
let direction = "RIGHT";

const interpolate = (min, max, fract) => max + (min - max) * fract;
const extrapolate = (first, second) => (second - first) + second


const Game = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Game",
  //@@viewOff:statics


  render: function (props) {
    //@@viewOn:hooks
    const [roomState, setRoomState] = useState();
    const [gameState, setGameState] = useState();
    let currentGameState = useRef(gameState);
    const [waiting, setWaiting] = useState(false);
    const [connectionError, setConnectionError] = useState(null);
    const [themeMusicPlays, setThemeMusicPlays] = useState(false);
    const [currentUser, setCurrentUser] = useState(false);
    const canvasRef = useRef();
    const {addExplosion} = RenderHelper()
    const {shake} = ShakeHelper()


    useEffect(() => {

      setCurrentUser()
      let fetch = true;

      let eventSource;

      let movesInTick = []

      async function poll() {
        eventSource = new EventSource(`${Calls.getCommandUri("sse")}?roomId=${props.params.roomId}`);
        eventSource.onmessage = (event) => {
          const result = JSON.parse(event.data);
          if (fetch === false) {
            return;
          }
          if (result.eventType === 'RoomEvent') {
            setRoomState(oldValue => ({...oldValue, ...result}));
          } else if (result.eventType === 'GameEvent') {
            setGameState(oldValue => {
              currentGameState = {
                ...oldValue,
                ...result
              };
              return currentGameState;
            });

            lastServerUpdateTimestamp = currentServerUpdateTimestamp;
            currentServerUpdateTimestamp = new Date().getTime();
            gameUpdateRate = (currentServerUpdateTimestamp - lastServerUpdateTimestamp)///1000.0;


            async function sendMoves(moves) {
              await Calls.gameInstanceAddPlayerMove({roomId: props.params.roomId, playerMoves: moves})
            }


            if (moveList.length) {
              const oldMoves = movesInTick[result.output.tick]
              oldMoves && moveList.splice(0, oldMoves.length);
              movesInTick[result.output.tick + 1] = [...moveList];
              sendMoves(movesInTick[result.output.tick + 1])
            }

          }
        };

        eventSource.onopen = (event) => {
          console.log("Open", event);
          moveList = [];
          movesInTick = []
        };

        eventSource.onerror = (event) => {
          console.log("Error", event);
        };


      }

      async function joinToRoom() {
        const room = await Calls.roomJoin({roomId: props.params.roomId})
        setRoomState(room);
      }


      document.body.addEventListener("keydown", sendPositionDown);
      document.body.addEventListener("keyup", sendPositionUp);
      document.body.addEventListener("mousemove", mouseMove);
      document.body.addEventListener("mousedown", mouseDown);

      if (props?.params?.roomId) {
        setWaiting(true);
        joinToRoom();
        poll();
        setWaiting(false);
      }
      return () => {
        fetch = false;
        eventSource && eventSource.close();
        setGameState({});
        setRoomState({});
        themeMusic.pause();
        setThemeMusicPlays(false);
        setWaiting(true);
        document.removeEventListener("keydown", sendPositionDown);
        document.removeEventListener("keyup", sendPositionUp);

      }
    }, [props?.params?.roomId]);

    useEffect(() => {

      if (themeMusic && !themeMusicPlays && roomState?.state === "ACTIVE") {
        themeMusic.loop = true;
        themeMusic.currentTime = 0;
        themeMusic.play();
        setThemeMusicPlays(true);
      }

    }, [themeMusicPlays, roomState?.state]);


    //@viewOff:hooks

    //@@viewOn:private

    function drawItem(event) {
      ctx.fillRect(event.clientX, event.clientY, 5, 5); // fill in the pixel at (10,10)
    }


    async function startGameHandler() {
      const result = await Calls.gameInstanceStartGame({roomId: props.params.roomId})
    }

    function getAngle(cx, cy, ex, ey) {
      let dy = ey - cy;
      let dx = ex - cx;
      let theta = Math.atan2(dy, dx); // range (-PI, PI]
      theta *= 180 / Math.PI; // rads to degs, range (-180, 180]
      //if (theta < 0) theta = 360 + theta; // range [0, 360)
      return theta;
    }


    function mouseMove(e) {

      // get current player
      const keyGameState = currentGameState?.output?.game ?? [];
      let playersState = JSON.parse(JSON.stringify(keyGameState.players));
      const currentPlayer = playersState[JSON.stringify(UU5.Environment.getSession().getIdentity().uuIdentity)];

      let canvasRect = canvasRef.current.getBoundingClientRect();

      currentPlayer.x;
      currentPlayer.y;
      currentPlayer.width;
      currentPlayer.height;

      // get player X and Y
      let x1 = currentPlayer.x + (currentPlayer.width / 2);
      let y1 = currentPlayer.y + (currentPlayer.height / 2);
      let x2 = e.x - canvasRect.left; //- leftOffset;
      let y2 = e.y - canvasRect.top; //- topOffset;

      angle = getAngle(x1, y1, x2, y2);

    }

    function mouseDown(event) {
      if (!event.shiftKey) {
        const newMove = {}
        newMove.fired = "BULLET"; // TODO - fire proper projectile type
        newMove.firedAngle = angle;
        moveList.push(newMove);
      }
    }


    function sendPositionDown(event) {
      console.info("downEvent");

      switch (event.key) {
        case 'ArrowLeft':
        case 'a':
          direction = "LEFT";
          moving = true;
          event.preventDefault();
          break;
        case 'ArrowRight':
        case 'd':
          direction = "RIGHT";
          moving = true;
          event.preventDefault();
          break;
        case 'ArrowUp':
        case 'w':
          const newMove = {}
          newMove.move = "UP";
          moveList.push(newMove);
          event.preventDefault();
          break;
      }

    }

    function sendPositionUp(event) {
      switch (event.key) {
        case 'ArrowLeft':
        case 'a':
        case 'ArrowRight':
        case 'd':
          moving = false;
          break;
      }
    }

    // todo - colorize rendered players, not used now
    const playerColors = [];
    const getPlayerColors = (index) => {
      if (playerColors[index] === undefined) {
        playerColors[index] = "#" + Math.floor(Math.random() * (16777215)).toString(16);
      }
      return playerColors[index];
    }

    let debug = 0;
    let debugInterval;
    let debugFlag = true;

    function animatePlayer(debug, ctx, player) {
      const img = new Image();
      let i = debug % 9;

      let path = `../assets/gun_idle/E_E_Gun__Idle_00${i}.png`;

      console.info("animate player:");
      console.info(path);

      img.src = path;
      ctx.drawImage(img, player.x, player.y, 60, 60);
      debug++;
    }

    function drawPlayers(ctx, players, playerIds, state, maxLives) {
      for (let i = 0; i < players.length; i++) {
        const player = players[i];
        const playerId = playerIds[i];


        // todo  if animation interval for this user exits, clear it

        // todo create new animation interval and store it to playerAnimation array
        // playerAnimation[playerId] = {interval:setInterval(animatePlayer.bind(null, debug,ctx, player),33), state:"idle"}
        //
        // if(playerAnimation[playerId]?.state  === state)){
        //
        // }

        const img = new Image();
        img.src = `../assets/gun_idle/E_E_Gun__Idle_000.png`;


        // dubugInterval = setInterval(function(playerId) {
        //
        //   const img = new Image();
        //   let i = debug % 9;
        //   img.src = `../assets/gun_idle/E_E_Gun__Idle_00${i}.png`;
        //   debug++;
        // },33);

        // if(debug > 1000){
        //   clearInterval(myInterval);
        // }
        // var foo = function () {
        //   clearInterval(myInterval);
        // };

        // ctx.drawImage(img, player.x, player.y, 60, 60);

        switch (player.direction) {
          case 'RIGHT':
            img.src = `../assets/gun_idle/E_E_Gun__Idle_000.png`;
            break;
          case 'LEFT':
            img.src = `../assets/gun_idle/E_E_Gun__Idle_000_left.png`;
            break;
        }


        if (player.lives > 0) {
          // plyer still breathing
          ctx.drawImage(img, player.x, player.y, player.width, player.height);
        } else {
          // draw grave
          const imgGrave = new Image();
          imgGrave.src = `../assets/graveTest.svg`;
          ctx.drawImage(imgGrave, player.x, player.y+2, player.width, player.height);
        }




        // draw player lifespan bar
        // =========================

        // life bar should be rendered only if player is still alive
        if (player.lives > 0) {

          let lifeSpan = player.width + 20;
          let lifePoint = lifeSpan / maxLives;

          let maxBarWidth = player.width + 20;
          let barLifeWidth = player.lives * lifePoint;

          if (barLifeWidth > maxBarWidth) {
            barLifeWidth = maxBarWidth;
          }
          ctx.fillStyle = 'rgba(255,0,0,0.6)'
          ctx.fillRect(player.x - 15, player.y - player.height + 5, player.width + 20, 10);
          if (maxLives < player.lives) {
            // player is in immortal mode, set gold color
            ctx.fillStyle = 'rgba(255,215,0,1)'
          } else {
            ctx.fillStyle = 'rgba(0,255,0,0.6)'
          }
          ctx.fillRect(player.x - 15, player.y - player.height + 5, barLifeWidth, 10);
        } // End of life bar rendering


        // player is freezed
        // ==================
        let freezed = player.speed === 0;
        if (freezed) {
          ctx.globalAlpha = 0.65;
          const imgIce = new Image();
          imgIce.src = `../assets/iceBlock.png`;
          ctx.drawImage(imgIce, player.x - 13, player.y - 20, player.width + 20, player.height + 25);
          ctx.globalAlpha = 1;
        }

        // todo draw gun separatly with current angle rotation
        //drawGuns(ctx, player);

      }
    }


    function drawAmmo(ctx, ammos) {
      for (let i = 0; i < ammos.length; i++) {
        const ammo = ammos[i];

        const img = new Image();
        if (ammo.type === "Bullet") {
          img.src = `../assets/Bullet.png`;
        }
        if (ammo.type === "Granate") {
          img.src = `../assets/Bomb.png`;
        }

        ctx.drawImage(img, ammo.x, ammo.y, ammo.width, ammo.height);

      }
    }

    function drawSpecialItems(ctx, items) {

      for (let i = 0; i < items.length; i++) {
        const specialItem = items[i];
        console.info(specialItem.specialAbilityName);

        const img = new Image();

        switch (specialItem.specialAbilityName) {
          case 'ImmortalitySpecialAbility':
            img.src = `../assets/bonus_imortal2.png`;
            break;
          case 'IncreasedSpeedSpecialAbility':
            img.src = `../assets/bonus_speed.png`;
            break;
          case 'IncreasedSpeedSpecialAbility':
            img.src = `../assets/bonus_speed.png`;
            break;
          case 'IncreaseLiveSpecialAbility':
            img.src = `../assets/bonus_life.png`;
            break;
        }

        ctx.drawImage(img, specialItem.x, specialItem.y, specialItem.width, specialItem.height);

      }

    }

    function drawObstacles(ctx, obstacles) {
      for (let i = 0; i < obstacles.length; i++) {
        const obstacle = obstacles[i]
        for (const wall of obstacle.walls) {
          if (obstacle.type === "TREE") {
            const img = new Image();
            img.src = "../assets/tree.png"
            ctx.drawImage(img, wall.x, wall.y, wall.width, wall.height);
          } else if (obstacle.type === "HOUSE") {
            const img = new Image();
            img.src = "../assets/house.png"
            ctx.drawImage(img, wall.x, wall.y, wall.width, wall.height);
          } else if (obstacle.type === "TAVERN") {
            const img = new Image();
            img.src = "../assets/tavern.png"
            ctx.drawImage(img, wall.x, wall.y, wall.width, wall.height);
          } else if (obstacle.type === "WOODEN_BOX") {
            const img = new Image();
            img.src = "../assets/wood_box.png"
            //img.src = "../assets/Portal.gif"
            ctx.drawImage(img, wall.x, wall.y, wall.width, wall.height);
          } else if (obstacle.type === "METAL_BOX") {
            const img = new Image();
            img.src = "../assets/metal_box.png"
            ctx.drawImage(img, wall.x, wall.y, wall.width, wall.height);
          } else if (obstacle.type === "WALL") {
            const img = new Image();
            img.src = "../assets/wall2.png"
            ctx.drawImage(img, wall.x, wall.y, wall.width, wall.height);
          } else {
            ctx.fillStyle = "yellow" // todo obstacle.type
            ctx.fillRect(wall.x, wall.y, wall.width, wall.height);
          }
        }
        // for (const wall of obstacle) {
        //
        // }
      }
    }


    function renderEvents(ctx, frameCount, events) {
      if (frameCount !== 1) {
        return
      }

      for (let i = 0; i < events?.length; i++) {
        const event = events[i]
        if (event.objectName === "Bullet") {
          if (event.action === "used") {
            const bulletStopSound = new Audio("../assets/fired.mp3");
            bulletStopSound.play();
            addExplosion(ctx, event.data.x, event.data.y)
          }

          if (event.action === "fired") {
            const firedSound = new Audio("../assets/fired.mp3");
            firedSound.play();
            // canvasRef && canvasRef.current && shake(canvasRef) //todo make it works - nejde mi použít ref
          }
        }
      }
    }


    const draw = (ctx, frameCount) => {

      if (moving) {
        const newMove = {}
        newMove.move = direction;
        moveList.push(newMove);
      }

      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height)
      ctx.fillStyle = '#000000'

      const grass = new Image();
      grass.src = "../assets/background_clouds.png"
      ctx.drawImage(grass, 0, 0, ctx.canvas.width, ctx.canvas.height);

      const keyGameState = gameState?.output?.game ?? []
      let amoState = [...keyGameState.ammo];
      let bonusItemsState = [...keyGameState.bonusItemList];
      let playersState = JSON.parse(JSON.stringify(keyGameState.players));

      let maxLives = gameState?.output?.params.initialLives ?? 5;

      if (frameCount !== 1) {
        const framePaint = 16
        for (let i = 0; i < amoState.length; i++) {
          const interpolation = (amoState[i].bulletSpeed) * (framePaint / gameUpdateRate)

          switch (amoState[i].direction) {
            case 'RIGHT':
              amoState[i].x = amoState[i].x + interpolation
              break;
            case 'LEFT':
              amoState[i].x = amoState[i].x - interpolation
              break;
          }


        }
      }


      const currentPlayer = playersState[JSON.stringify(UU5.Environment.getSession().getIdentity().uuIdentity)]
      for (let i = 0; i < moveList.length; i++) {

        switch (moveList[i].move) {
          case 'RIGHT':
            currentPlayer.direction = moveList[i].move;
            currentPlayer.x = currentPlayer.x + currentPlayer.speed
            break;
          case 'LEFT':
            currentPlayer.direction = moveList[i].move;
            currentPlayer.x = currentPlayer.x - currentPlayer.speed
            break;
        }


      }
      playersState[JSON.stringify(UU5.Environment.getSession().getIdentity().uuIdentity)] = currentPlayer;

      //todo extrapolate move - do only once

      //todo intrapolate move - do for every frame


      if (keyGameState) {

        // render state

        drawPlayers(ctx, Object.values(playersState), Object.keys(playersState), "?", maxLives);


        drawAmmo(ctx, amoState)

        drawObstacles(ctx, keyGameState.obstacles);

        drawSpecialItems(ctx, bonusItemsState);
        // todo add rander special items

        // render events
        renderEvents(ctx, frameCount, gameState?.output?.gameEvents)
      }

      ctx.fill()
    }


    //@@viewOff:private

    //@@viewOn:render
    if (waiting) {
      return <UU5.Bricks.Loading/>
    }

    return (
      <UU5.Bricks.Section level={1} header="Game" className={UU5.Common.Css.css`padding: 16px`}>


        Updated upstream
        Connected players: {roomState?.connectedPlayers?.map((p) => p.playerId)}


        <UU5.Bricks.Card className="uu5-common-padding-s" ref={canvasRef} style={{
          border: "1px solid black",
          minHeight: "550px",
          width: "100%"
        }}>
          {gameState?.output?.state === "RUNNING" && gameState?.output?.tick > 0 ?
            <div ref={canvasRef}>
              <Canvas draw={draw}

              />
            </div>
            :
            <>
              Connected players: {roomState?.output?.connectedPlayers?.map((p) => p.playerId)}
              {connectionError}

              {/*todo fix this*/}
              {/*{roomState?.output?.connectedPlayers?.map((p) => <Plus4U5.Bricks.BusinessCard visual="micro" uuIdentity={p.playerId}/>)}*/}

              <UU5.Bricks.Paragraph>
                {gameState?.output?.state === "WAITING" && "Waiting for next players"}
                {gameState?.output?.state === "WAITING" && "Move player with arrows or A W D, Fire on mouse left click."}
                {gameState?.output?.state === "FINISHED" && "Game is finished"}
                {/*{gameState?.output?.state === "FINISHED" && gameState?.output?.game?.players?.map((p) =>*/}
                {/*  (<UU5.Bricks.Paragraph>{p.playerId} - {p.score}</UU5.Bricks.Paragraph>))}*/}
              </UU5.Bricks.Paragraph>
              <UU5.Bricks.TouchIcon content="Click" colorSchema="primary"/>
              <UU5.Bricks.Button onClick={startGameHandler} colorSchema={"primary"}>Start new game<UU5.Bricks.Icon
                icon="mdi-apple"/></UU5.Bricks.Button>
            </>
          }

        </UU5.Bricks.Card>


        {gameState?.output?.tick > 0 &&
        <UU5.Bricks.Button onClick={startGameHandler} colorSchema={"secondary"}>Restart game<UU5.Bricks.Icon
          icon="mdi-apple"/></UU5.Bricks.Button>}


        <UU5.Bricks.Paragraph content={"debug"}/>

        <UU5.Bricks.Panel header={props?.params?.roomId} content={<pre>
          RoomId :: {props?.params?.roomId}
        </pre>}/>

        <UU5.Bricks.Panel header="gameState debug" content={<pre>
          GAME: {JSON.stringify(gameState, undefined, 2)}
        </pre>}/>

        <UU5.Bricks.Panel header="roomState debug" content={<pre>
          ROOM: {JSON.stringify(roomState, undefined, 2)}
        </pre>}/>


      </UU5.Bricks.Section>
    );
    //@@viewOff:render
  },
});

export default Game;
