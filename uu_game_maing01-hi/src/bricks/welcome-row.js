//@@viewOn:imports
import UU5 from "uu5g04";
import "uu5g04-bricks";
import { createVisualComponent } from "uu5g04-hooks";

import Config from "./config/config.js";
//@@viewOff:imports

const STATICS = {
  //@@viewOn:statics
  displayName: Config.TAG + "WelcomeRow",
  //@@viewOff:statics
};

const CLASS_NAMES = {
  main: () => Config.Css.css`
    padding: 24px 0;
    max-width: 624px;
    margin: 0 auto;
  `,
  text: () => Config.Css.css`
    text-align: center;

    ${UU5.Utils.ScreenSize.getMinMediaQueries("s", `text-align: left;`)}
  `,
  iconColumn: () => Config.Css.css`
    padding-right: 24px;
    text-align: center;
  
    ${UU5.Utils.ScreenSize.getMinMediaQueries("s", `text-align: right;`)}
  
    .uu5-bricks-icon {
      font-size: 48px;
    }
  `,
  icon: (cssMargin) => Config.Css.css`
    margin-top: ${cssMargin};
    margin-bottom: ${cssMargin};
  `,
};

export const WelcomeRow = createVisualComponent({
  ...STATICS,

  //@@viewOn:propTypes
  propTypes: {
    icon: UU5.PropTypes.string,
    textPadding: UU5.PropTypes.string,
  },
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  defaultProps: {
    icon: undefined, // default of UU5.Bricks.Icon
    textPadding: null,
  },
  //@@viewOff:defaultProps

  render(props) {
    //@@viewOn:private
    let { icon, textPadding, children } = props;
    //@@viewOff:private

    //@@viewOn:interface
    //@@viewOff:interface

    //@@viewOn:render
    let cssMargin = UU5.Common.Tools.fillUnit("-" + textPadding);
    let attrs = UU5.Common.VisualComponent.getAttrs(props, CLASS_NAMES.main());
    return (
      <UU5.Bricks.Row {...attrs}>
        <UU5.Bricks.Column className={CLASS_NAMES.iconColumn()} colWidth="xs-12 s-2">
          <UU5.Bricks.Icon icon={icon} className={CLASS_NAMES.icon(cssMargin)} />
        </UU5.Bricks.Column>
        <UU5.Bricks.Column
          className={CLASS_NAMES.text()}
          colWidth="xs-12 s-10"
          content={UU5.Utils.Content.getChildren(children, props, STATICS)}
        />
      </UU5.Bricks.Row>
    );
    //@@viewOff:render
  },
});

export default WelcomeRow;
