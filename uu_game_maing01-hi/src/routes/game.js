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
const gunW = 5;
const gunH = 10;


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


      document.body.addEventListener("keydown", sendPosition);
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

    function sendPosition(event) {
      //console.log(currentGameState);
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
          direction = "UP";
          break
        // case 'ArrowDown':
        //   gameMoved = true;
        //   direction = "DOWN";
        //   break;

        // case "R":
        //   gameMoved = true;
        //   reload = true;
        //   break;
      }

      if (gameMoved) {
        const newMove = {}
        newMove.move = direction;

        if (event.shiftKey) {
          newMove.sprinting = true;
          fired = null;
        }

        moveList.push(newMove);
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

    let debug = 0;
    let dubugInterval;

    // function animatePlayer(debug, ctx, player){
    //   const img = new Image();
    //   let i = debug % 9;
    //   img.src = `../assets/gun_idle/E_E_Gun__Idle_00${i}.png`;
    //   ctx.drawImage(img, player.x, player.y, 60, 60);
    //   debug++;
    // }

    function drawPlayers(ctx, players, playerIds, state) {
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
        //ctx.drawImage(img, player.x, player.y, 60, 60);

          // dubugInterval = setInterval(function(playerId) {
        //   // todo
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

        // ctx.fillStyle = "blue";
        // ctx.fillRect(player.x, player.y, player.width, player.height);
        // ctx.fillStyle = "red";
         switch (player.direction) {
           case 'RIGHT':
             img.src = `../assets/gun_idle/E_E_Gun__Idle_000.png`;
             break;
           case 'LEFT':
             img.src = `../assets/gun_idle/E_E_Gun__Idle_000_left.png`;
             break;
          // case 'UP':
          //   img.src = `../assets/gun_idle/E_E_Gun__Idle_000_up.png`;
          //   break;
          // case 'DOWN':
          //   img.src = `../assets/gun_idle/E_E_Gun__Idle_000_left.png`;
          //   break;
         }

         //ctx.rotate(30*Math.PI/180.0);
         //img.setAttribute("style", "transform: rotate(" + 30 + "deg)");

         ctx.drawImage(img, player.x, player.y, player.width, player.height);
        // todo draw gun
        drawGuns(ctx, player);

      }
    }

    function drawGuns(ctx, player) {
      // todo - check if this player is current player
      ctx.fillStyle = "red";
      //ctx.fillRect(player.x + player.width, player.y + (player.height / 2), 5, 10);
      if (player.id === JSON.stringify(UU5.Environment.getSession().getIdentity().uuIdentity)) {
        // current user
        // draw gun by local angle
        let x1 = player.x + (player.width / 2);
        let y1 = player.y + (player.height / 2);

        let angle = angle * (Math.PI / 180);
        let x2 = x1 + gunH * Math.cos(angle);
        let y2 = y1 + gunH * Math.sin(angle);

        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x2, y2);
        gun.x = x2;
        gun.y = y2;
        ctx.lineWidth = gunW;
        ctx.stroke();
      }
    }

    function drawAmmo(ctx, ammos) {
      for (let i = 0; i < ammos.length; i++) {
        const ammo = ammos[i];

        const img = new Image();
        if(ammo.type === "Bullet"){
          img.src = `../assets/Bullet.png`;
        }
        if(ammo.type === "Granate"){
          img.src = `../assets/Bomb.png`;
        }

        ctx.drawImage(img, ammo.x, ammo.y, ammo.width, ammo.height);


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
          } else if (obstacle.type === "WOOD_BOX") {
            const img = new Image();
            img.src = "../assets/wood_box.png"
            ctx.drawImage(img, wall.x, wall.y, wall.width, wall.height);
          } else if (obstacle.type === "METAL_BOX") {
            const img = new Image();
            img.src = "../assets/metal_box.png"
            ctx.drawImage(img, wall.x, wall.y, wall.width, wall.height);
          } else if (obstacle.type === "WALL") {
            const img = new Image();
            img.src = "../assets/wall.png"
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
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height)
      ctx.fillStyle = '#000000'

      const grass = new Image();
      grass.src = "../assets/background_clouds.png"
      ctx.drawImage(grass, 0, 0, ctx.canvas.width, ctx.canvas.height);

      const keyGameState = gameState?.output?.game ?? []
      let amoState = [...keyGameState.ammo];
      let playersState = JSON.parse(JSON.stringify(keyGameState.players));

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
            // case 'UP':
            //   amoState[i].y = amoState[i].y + interpolation
            //   break;
            // case 'DOWN':
            //   amoState[i].y = amoState[i].y - interpolation
            //   break;
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
          // case 'DOWN':
          //   currentPlayer.direction = moveList[i].move;
          //   currentPlayer.y = currentPlayer.y + currentPlayer.speed
          //   break;
          // case 'UP':
          //   currentPlayer.direction = moveList[i].move;
          //   currentPlayer.y = currentPlayer.y - currentPlayer.speed
          //   break;
        }


      }
      playersState[JSON.stringify(UU5.Environment.getSession().getIdentity().uuIdentity)] = currentPlayer;

      //todo extrapolate move - do only once

      //todo intrapolate move - do for every frame


      if (keyGameState) {

        // render state

        drawPlayers(ctx, Object.values(playersState), Object.keys(playersState));

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
                {gameState?.output?.state === "FINISHED" && "Game is finished"}
                {gameState?.output?.state === "FINISHED" && gameState?.output?.game?.players?.map((p) =>
                  (<UU5.Bricks.Paragraph>{p.playerId} - {p.score}</UU5.Bricks.Paragraph>))}
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
