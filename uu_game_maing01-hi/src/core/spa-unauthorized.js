//@@viewOn:imports
import UU5 from "uu5g04";
import "uu5g04-bricks";
import { createVisualComponent } from "uu5g04-hooks";

import Config from "./config/config.js";

import Lsi from "../config/lsi.js";
//@@viewOff:imports

const STATICS = {
  //@@viewOn:statics
  displayName: Config.TAG + "SpaUnauthorized",
  //@@viewOff:statics
};

const CLASS_NAMES = {
  main: () => Config.Css.css`
    width: 390px;
    max-width: 100%;
    margin: 56px auto 0;
    text-align: center;
  `,
  errorIcon: () => Config.Css.css`
    font-size: 40px;
    color: ${UU5.Environment.colors.grey.c500};
  `,
  textError: () => Config.Css.css`
    margin: 16px 0 32px;
    font-size: 22px;
    color: ${UU5.Environment.colors.grey.c600};
  `,
};

export const SpaUnauthorized = createVisualComponent({
  ...STATICS,

  //@@viewOn:propTypes
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  //@@viewOff:defaultProps

  //@@viewOn:render
  render(props) {
    //@@viewOn:private
    let { children } = props;
    //@@viewOff:private

    //@@viewOn:interface
    //@@viewOff:interface

    //@@viewOn:render
    const attrs = UU5.Common.VisualComponent.getAttrs(props, CLASS_NAMES.main());
    return (
      <div {...attrs}>
        <UU5.Bricks.Icon className={CLASS_NAMES.errorIcon()} icon="mdi-block-helper" />
        <UU5.Bricks.Text className={CLASS_NAMES.textError()}>
          {UU5.Utils.Content.getChildren(children, props, STATICS) || <UU5.Bricks.Lsi lsi={Lsi.unauth.notAuthorized} />}
        </UU5.Bricks.Text>
        <UU5.Bricks.Button colorSchema="primary">
          <UU5.Bricks.Lsi lsi={Lsi.unauth.continueToMain} />
        </UU5.Bricks.Button>
      </div>
    );
    //@@viewOff:render
  },
});

export default SpaUnauthorized;
