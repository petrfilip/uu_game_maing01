//@@viewOn:imports
import Calls from "calls";
import UU5 from "uu5g04";
import "uu5g04-bricks";
import "uu5g04-forms";
import { createVisualComponent, useData } from "uu5g04-hooks";
import UuTerritory from "uu_territoryg01";
import "uu_territoryg01-artifactifc";
import Plus4U5 from "uu_plus4u5g01";
import "uu_plus4u5g01-app";
import UuContentKit from "uu_contentkitg01";

import Config from "./config/config.js";
import Lsi from "../config/lsi.js";
//@@viewOff:imports

const STATICS = {
  //@@viewOn:statics
  displayName: Config.TAG + "ControlPanel",
  //@@viewOff:statics
};

export const ControlPanel = createVisualComponent({
  ...STATICS,

  //@@viewOn:propTypes
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  //@@viewOff:defaultProps

  render(props) {
    //@@viewOn:private
    let { viewState, asyncData: data } = useData({ onLoad: Calls.getWorkspace });
    //@@viewOff:private

    //@@viewOn:interface
    //@@viewOff:interface

    //@@viewOn:render
    let attrs = UU5.Common.VisualComponent.getAttrs(props);
    let child;
    let territoryBaseUri;
    let artifactId;
    if (viewState === "error") {
      child = (
        <Plus4U5.Bricks.Error error={data.dtoOut} errorData={data?.dtoOut?.uuAppErrorMap}>
          <UU5.Bricks.Lsi lsi={Lsi.controlPanel.rightsError} />
        </Plus4U5.Bricks.Error>
      );
    } else if (viewState === "load") {
      child = <UU5.Bricks.Loading />;
    } else if (data.artifactUri) {
      const url = new URL(data.artifactUri);
      url.pathname = url.pathname.split("/", 3).join("/");
      territoryBaseUri = url.href.split("?")[0];
      artifactId = url.searchParams.get("id");
      child = (
        <UuTerritory.ArtifactIfc.Bricks.PermissionSettings
          {...attrs}
          style={{ marginLeft: "30px", marginRight: "30px", width: "initial" }} // TODO Use className when uu_territory gets fixed (it ignores it now)
          territoryBaseUri={territoryBaseUri}
          artifactId={artifactId}
        />
      );
    } else {
      child = (
        <UuContentKit.Bricks.BlockDanger>
          <UU5.Bricks.Lsi lsi={Lsi.controlPanel.btNotConnected} />
        </UuContentKit.Bricks.BlockDanger>
      );
    }
    return (
      <UU5.Common.Fragment>
        {viewState !== "load" ? (
          <Plus4U5.App.ArtifactSetter territoryBaseUri={territoryBaseUri} artifactId={artifactId} />
        ) : null}
        {child}
      </UU5.Common.Fragment>
    );
    //@@viewOff:render
  },
});

export default ControlPanel;
