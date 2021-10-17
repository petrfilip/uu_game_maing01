//@@viewOn:imports
import UU5 from "uu5g04";
import "uu5g04-bricks";
import { createVisualComponent } from "uu5g04-hooks";
import Lsi from "../config/lsi.js";

import Config from "./config/config.js";
//@@viewOff:imports

const STATICS = {
  //@@viewOn:statics
  displayName: Config.TAG + "Bottom",
  //@@viewOff:statics
};

const CLASS_NAMES = {
  main: () => Config.Css.css`
    padding: 8px 0;
    text-align: center;
    border-top: 1px solid rgba(0, 0, 0, 0.12);
    color: gray;
  `,
};

export const Bottom = createVisualComponent({
  ...STATICS,

  //@@viewOn:propTypes
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  //@@viewOff:defaultProps

  render(props) {
    //@@viewOn:private
    //@@viewOff:private

    //@@viewOn:interface
    //@@viewOff:interface

    //@@viewOn:render
    const attrs = UU5.Common.VisualComponent.getAttrs(props, CLASS_NAMES.main());
    return (
      <div {...attrs}>
        uuGameMaing01-{process.env.VERSION} © Unicorn,{" "}
        <UU5.Bricks.Link target="_blank" href="TODO">
          <UU5.Bricks.Lsi lsi={Lsi.bottom.termsOfUse} />
        </UU5.Bricks.Link>
      </div>
    );
    //@@viewOff:render
  },
});

export default Bottom;
