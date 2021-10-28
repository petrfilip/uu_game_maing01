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
          if (fetch === false) {
            return;
          }
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
        setGameState({})
        setRoomState({})
        setWaiting(true)
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


    if (waiting) {
      return <UU5.Bricks.Loading/>
    }

    return (
      <UU5.Bricks.Section level={1} header="Game" className={UU5.Common.Css.css`padding: 16px`}>


        Connected players: {roomState?.output?.connectedPlayers?.map((p) => p.playerId)}


        <UU5.Bricks.Card className="uu5-common-padding-s" style={{
          border: "1px solid black",
          height: "500px",
          width: "100%"
        }}>
          {gameState?.output?.tick > 0 ?
            <Canvas draw={draw}
                    onMouseDown={() => document.addEventListener("mousemove", sendPosition)}
                    onMouseUp={() => document.removeEventListener("mousemove", sendPosition)}
            />
            :
            <>
              Connected players: {roomState?.output?.connectedPlayers?.map((p) => p.playerId)}

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
