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


const Game = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Game",
  //@@viewOff:statics

  render: function (props) {
    //@@viewOn:hooks
    const [roomState, setRoomState] = useState();
    const [gameState, setGameState] = useState();
    const [waiting, setWaiting] = useState(false);
    const [connectionError, setConnectionError] = useState(null);
    const [themeMusicPlays, setThemeMusicPlays] = useState(false);
    const canvasRef = useRef()
    const {addExplosion} = RenderHelper()
    const {shake} = ShakeHelper()


    useEffect(() => {
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
            setGameState(oldValue => ({...oldValue, ...result}));

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


      document.addEventListener("keydown", sendPosition)

      if (props?.params?.roomId) {


        setWaiting(true);
        joinToRoom();
        poll();
        setWaiting(false);

      }
      return () => {
        fetch = false;
        eventSource && eventSource.close();
        setGameState({})
        setRoomState({})
        themeMusic.pause()
        setThemeMusicPlays(false)
        setWaiting(true)
        document.removeEventListener("keydown", sendPosition)

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


    function sendPosition(event) {
      let direction;
      let sprinting = false;
      let fired = null;
      let reload = false;
      let gameMoved = false;
      switch (event.key) {
        case 'ArrowLeft':
          gameMoved = true;
          direction = "LEFT";
          break;
        case 'ArrowRight':
          gameMoved = true;
          direction = "RIGHT";
          break;
        case 'ArrowUp':
          gameMoved = true;
          direction = "DOWN"; // this is switched with DOWN because axes translation
          break
        case 'ArrowDown':
          gameMoved = true;
          direction = "UP"; // this is switched with DOWN because axes translation
          break;
        case "R":
          gameMoved = true;
          reload = true;
          break;
        case ' ':
          gameMoved = true;
          fired = "BULLET"
          break;

      }
      if (event.shiftKey) {
        sprinting = true;
        fired = null;
      }
      if (gameMoved) {
        const newMove = {}
        newMove.move = direction;
        if (fired) {
          newMove.fired = fired
        }
        moveList.push(newMove)
        event.preventDefault();
      }
    }


    const playerColors = [];

    const getPlayerColors = (index) => {
      if (playerColors[index] === undefined) {
        playerColors[index] = "#" + Math.floor(Math.random() * (16777215)).toString(16);
      }
      return playerColors[index];
    }

    function drawPlayers(ctx, players) {
      for (let i = 0; i < players.length; i++) {
        const player = players[i]
        ctx.fillStyle = "blue";
        ctx.fillRect(player.x, player.y, player.width, player.height);
        ctx.fillStyle = "red";
        switch (player.direction) {
          case 'RIGHT':
            ctx.fillRect(player.x + player.width, player.y + (player.height / 2), 5, 5);
            break;
          case 'LEFT':
            ctx.fillRect(player.x - 5, player.y + (player.height / 2), 5, 5);
            break;
          case 'UP':
            ctx.fillRect(player.x + (player.width / 2), player.y + player.width, 5, 5);
            break;
          case 'DOWN':
            ctx.fillRect(player.x + (player.width / 2), player.y - 5, 5, 5);
            break;
        }
      }
    }

    function drawAmmo(ctx, ammos) {
      for (let i = 0; i < ammos.length; i++) {
        const ammo = ammos[i]
        ctx.fillStyle = "red"
        ctx.fillRect(ammo.x, ammo.y, ammo.width, ammo.height);
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
            canvasRef && canvasRef.current && shake(canvasRef) //todo make it works - nejde mi použít ref
          }
        }

      }
    }


    const draw = (ctx, frameCount) => {
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height)
      ctx.fillStyle = '#000000'

      const grass = new Image();
      grass.src = "../assets/grass.png"
      ctx.drawImage(grass, 0, 0, ctx.canvas.width, ctx.canvas.height);

      const keyGameState = gameState?.output?.game ?? []
      let amoState = [...keyGameState.ammo];
      let playersState = JSON.parse(JSON.stringify(keyGameState.players));

      if (frameCount !== 1) {
        const gameInterval = 500
        const framePaint = 16
        for (let i = 0; i < amoState.length; i++) {
          const interpolation = (amoState[i].bulletSpeed) * (framePaint / gameInterval)

          switch (amoState[i].direction) {
            case 'RIGHT':
              amoState[i].x = amoState[i].x + interpolation
              break;
            case 'LEFT':
              amoState[i].x = amoState[i].x - interpolation
              break;
            case 'UP':
              amoState[i].y = amoState[i].y + interpolation
              break;
            case 'DOWN':
              amoState[i].y = amoState[i].y - interpolation
              break;
          }


        }
      }


      const currentPlayer = playersState[JSON.stringify(UU5.Environment.getSession().getIdentity().uuIdentity)]
      for (let i = 0; i < moveList.length; i++) {

        // if (moveList[i].move !== currentPlayer.direction) {
        //   debugger
        //   currentPlayer.direction = moveList[i].move;
        // }

        switch (moveList[i].move) {
          case 'RIGHT':
            currentPlayer.direction = moveList[i].move;
            currentPlayer.x = currentPlayer.x + currentPlayer.speed
            break;
          case 'LEFT':
            currentPlayer.direction = moveList[i].move;
            currentPlayer.x = currentPlayer.x - currentPlayer.speed
            break;
          case 'UP':
            currentPlayer.direction = moveList[i].move;
            currentPlayer.y = currentPlayer.y + currentPlayer.speed
            break;
          case 'DOWN':
            currentPlayer.direction = moveList[i].move;
            currentPlayer.y = currentPlayer.y - currentPlayer.speed
            break;
        }


      }
      playersState[JSON.stringify(UU5.Environment.getSession().getIdentity().uuIdentity)] = currentPlayer;


      if (keyGameState) {

        // render state
        drawPlayers(ctx, Object.values(playersState))
        drawAmmo(ctx, amoState)
        drawObstacles(ctx, keyGameState.obstacles);

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


        Connected players: {roomState?.output?.connectedPlayers?.map((p) => p.playerId)}


        <UU5.Bricks.Card className="uu5-common-padding-s" ref={canvasRef} style={{
          border: "1px solid black",
          minHeight: "550px",
          width: "100%"
        }}>
          {gameState?.output?.tick > 0 ?
            <Canvas draw={draw}
              // onMouseDown={() => document.addEventListener("mousemove", sendPosition)}
              // onMouseUp={() => document.removeEventListener("mousemove", sendPosition)}
            />
            :
            <>
              Connected players: {roomState?.output?.connectedPlayers?.map((p) => p.playerId)}
              {connectionError}

              {/*todo fix this*/}
              {/*{roomState?.output?.connectedPlayers?.map((p) => <Plus4U5.Bricks.BusinessCard visual="micro" uuIdentity={p.playerId}/>)}*/}

              <UU5.Bricks.Paragraph> GAME DESCRIPTION: Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Class aptent taciti sociosqu ad
                litora torquent per conubia nostra, per inceptos hymenaeos. Donec ipsum massa, ullamcorper in,
                auctor et, scelerisque sed, est. Vestibulum fermentum tortor id mi. Etiam commodo dui eget wisi.
                Integer malesuada. Fusce consectetuer risus a nunc. Nullam eget nisl. In sem justo, commodo ut, suscipit at,
                pharetra vitae, orci. Aenean placerat. Etiam neque. Fusce suscipit libero eget elit.
              </UU5.Bricks.Paragraph>
              <UU5.Bricks.TouchIcon content="Click" colorSchema="primary"/>
              <UU5.Bricks.Button onClick={startGameHandler} colorSchema={"primary"}>Start new game<UU5.Bricks.Icon icon="mdi-apple"/></UU5.Bricks.Button>
            </>
          }

        </UU5.Bricks.Card>


        {gameState?.output?.tick > 0 &&
          <UU5.Bricks.Button onClick={startGameHandler} colorSchema={"secondary"}>Restart game<UU5.Bricks.Icon icon="mdi-apple"/></UU5.Bricks.Button>}


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
