//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent} from "uu5g04-hooks";
import Config from "./config/config";
//@@viewOff:imports

const Game = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Game",
  //@@viewOff:statics

  render(props) {
    //@@viewOn:hooks
    //@viewOff:hooks

    //@@viewOn:private

    //@@viewOff:private

    //@@viewOn:render
    return (
      <UU5.Bricks.Container>
        GAME :: {props?.params?.roomId}
      </UU5.Bricks.Container>
    );
    //@@viewOff:render
  },
});

export default Game;
