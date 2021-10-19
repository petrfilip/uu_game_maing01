//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent, useEffect, useState} from "uu5g04-hooks";
import Config from "./config/config";
import Calls from "../calls";
import Canvas from "../bricks/canvas";
//@@viewOff:imports

let GameState = {};

const Game = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Game",
  //@@viewOff:statics

  render(props) {
    //@@viewOn:hooks
    const [roomState, setRoomState] = useState();
    const [value, setValue] = useState();
    const [waiting, setWaiting] = useState(false);

    useEffect(async () => {
      if (props?.params?.roomId) {
        setWaiting(true);
        const room = await Calls.roomJoin({roomId: props.params.roomId})
        setRoomState(room);
        poll(); //todo abort request when room changed
        setWaiting(false);
      }
      return () => {
      }
    }, [props?.params?.roomId]);
    //@viewOff:hooks

    //@@viewOn:private

    function drawItem(event) {
      ctx.fillRect(event.clientX, event.clientY, 5, 5); // fill in the pixel at (10,10)
    }

    async function poll() {
      while (true) {
        const result = await Calls.poll({roomId: props.params.roomId});
        console.log(result)
        GameState = result;
        //todo if room or game event
      }
    }

    async function startGameHandler() {
      const result = await Calls.gameInstanceStartGame({roomId: props.params.roomId})
      setRoomState(result);
    }

    function sendPosition(event) {
      Calls.gameInstanceAddPlayerMove({roomId: props.params.roomId, playerMoves: {x: event.clientX, y: event.clientY}})
    }

    const draw = (ctx, frameCount) => {
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height)
      ctx.fillStyle = '#000000'

      console.log(GameState?.output?.game)

      const arr = GameState?.output?.game ? [...GameState?.output?.game] : []
      if (Array.isArray(arr)) {

        for (const element of arr) {

          console.log(element)
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

        <UU5.Bricks.Container>
          Value: {value}
        </UU5.Bricks.Container>

        <UU5.Bricks.Container>
          Room: {JSON.stringify(roomState)}
        </UU5.Bricks.Container>


        {roomState && <UU5.Bricks.Container>
          <Canvas draw={draw}
                  onMouseDown={() => document.addEventListener("mousemove", sendPosition)}
                  onMouseUp={() => document.removeEventListener("mousemove", sendPosition)}
          />
        </UU5.Bricks.Container>}


        <UU5.Bricks.Button onClick={startGameHandler}>Start game<UU5.Bricks.Icon icon="mdi-apple"/></UU5.Bricks.Button>
      </UU5.Bricks.Section>
    );
    //@@viewOff:render
  },
});

export default Game;
