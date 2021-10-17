//@@viewOn:imports
import { createVisualComponent } from "uu5g04-hooks";
import Plus4U5 from "uu_plus4u5g01";
import "uu_plus4u5g01-app";

import Config from "./config/config.js";
import SpaAuthenticated from "./spa-authenticated.js";
//@@viewOff:imports

const STATICS = {
  //@@viewOn:statics
  displayName: Config.TAG + "Spa",
  //@@viewOff:statics
};

export const Spa = createVisualComponent({
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
    return (
      <Plus4U5.App.Spa {...props} appName="uuGame">
        <SpaAuthenticated />
      </Plus4U5.App.Spa>
    );
    //@@viewOff:render
  },
});

export default Spa;
