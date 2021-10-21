//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent, useEffect, useState} from "uu5g04-hooks";
import Config from "./config/config";
import Calls from "../calls";
import Canvas from "../bricks/canvas";
//@@viewOff:imports

const Game = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Game",
  //@@viewOff:statics

  render(props) {
    //@@viewOn:hooks
    const [roomState, setRoomState] = useState();
    const [gameState, setGameState] = useState();
    const [waiting, setWaiting] = useState(false);


    useEffect(() => {
      let fetch = true;

      async function poll() {
        while (fetch) {
          console.log(props.params.roomId)
          const result = await Calls.poll({roomId: props.params.roomId});
          console.log(result)
          if (result.eventType === 'RoomEvent') {
            setRoomState(oldValue => ({...oldValue, ...result}));
          } else if (result.eventType === 'GameEvent') {
            setGameState(oldValue => ({...oldValue, ...result}));
          }
        }
      }


      if (props?.params?.roomId) {
        setWaiting(true);
        const room = Calls.roomJoin({roomId: props.params.roomId})
        setRoomState(room);
        poll(); //todo abort request when room changed
        setWaiting(false);
      }
      return () => {
        fetch = false;
      }
    }, [props?.params?.roomId]);
    //@viewOff:hooks

    //@@viewOn:private

    function drawItem(event) {
      ctx.fillRect(event.clientX, event.clientY, 5, 5); // fill in the pixel at (10,10)
    }


    async function startGameHandler() {
      const result = await Calls.gameInstanceStartGame({roomId: props.params.roomId})
    }

    function sendPosition(event) {
      Calls.gameInstanceAddPlayerMove({roomId: props.params.roomId, playerMoves: {x: event.clientX, y: event.clientY}})
    }

    const draw = (ctx, frameCount) => {
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height)
      ctx.fillStyle = '#000000'

      const arr = gameState?.output?.game ?? []
      if (Array.isArray(arr)) {

        for (const element of arr) {

          // console.log(element)
          ctx.fillStyle = "red";
          ctx.fillRect(element.x, element.y, 5, 5)
        }
      }

      ctx.fill()
    }
    //@@viewOff:private

    //@@viewOn:render
    return (
      <UU5.Bricks.Section level={1} header="Game" className={UU5.Common.Css.css`padding: 16px`}>

        <UU5.Bricks.Container>
          GAME :: {props?.params?.roomId}
        </UU5.Bricks.Container>

        <UU5.Bricks.Container>
          Waiting: {waiting}
        </UU5.Bricks.Container>

        <pre>
          GAME: {JSON.stringify(gameState, undefined, 2)}
        </pre>

        <pre>
          ROOM: {JSON.stringify(roomState, undefined, 2)}
        </pre>


        {roomState && <UU5.Bricks.Container>
          <Canvas draw={draw}
                  onMouseDown={() => document.addEventListener("mousemove", sendPosition)}
                  onMouseUp={() => document.removeEventListener("mousemove", sendPosition)}
          />
          Game is running
        </UU5.Bricks.Container>}


        <UU5.Bricks.Button onClick={startGameHandler}>Start game<UU5.Bricks.Icon icon="mdi-apple"/></UU5.Bricks.Button>
      </UU5.Bricks.Section>
    );
    //@@viewOff:render
  },
});

export default Game;
