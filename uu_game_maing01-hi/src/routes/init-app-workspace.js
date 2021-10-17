//@@viewOn:imports
import Calls from "calls";
import UU5 from "uu5g04";
import "uu5g04-bricks";
import "uu5g04-forms";
import { createVisualComponent, useData, useRef, useState, useCallback, useLsiValues } from "uu5g04-hooks";
import Plus4U5 from "uu_plus4u5g01";
import "uu_plus4u5g01-app";
import SpaUnauthorizedInit from "../core/spa-unauthorized-init.js";
import Config from "./config/config.js";
import Lsi from "./init-app-workspace-lsi.js";
//@@viewOff:imports

const RELATIVE_URI_REGEXP = new RegExp(/^\/[^/]/);

const STATICS = {
  //@@viewOn:statics
  displayName: Config.TAG + "InitAppWorkspace",
  //@@viewOff:statics
};

const CLASS_NAMES = {
  main: () => Config.Css.css`
    max-width: 512px;
    margin: auto;
    padding: 10px;
  `,
  cancelButton: () => Config.Css.css`
    display: none;
  `,
};

export const InitAppWorkspace = createVisualComponent({
  ...STATICS,

  //@@viewOn:propTypes
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  //@@viewOff:defaultProps

  render(props) {
    //@@viewOn:private
    const routeLsi = useLsiValues(Lsi);
    const formRef = useRef();
    let [initWorkspaceError, setInitWorkspaceError] = useState();
    let { viewState, asyncData: data } = useData({ onLoad: Calls.loadIdentityProfiles });

    let handleSave = useCallback(async ({ component, values }) => {
      try {
        let originalUrl = new URLSearchParams(window.location.search).get("originalUrl");
        let workspace = await Calls.initAndGetWorkspace(values);
        component.saveDone({ workspace, originalUrl });
      } catch (error) {
        component.saveFail(error);
      }
    }, []);

    let handleSaveDone = useCallback(({ dtoOut }) => {
      let { workspace, originalUrl } = dtoOut;
      let redirectPath;
      if (workspace && workspace.artifactUri) {
        redirectPath = UU5.Environment.getAppBasePath() + "controlPanel";
      } else if (originalUrl && RELATIVE_URI_REGEXP.test(originalUrl)) {
        redirectPath = originalUrl;
      } else {
        redirectPath = UU5.Environment.getAppBasePath();
      }
      window.location.replace(redirectPath);
    }, []);

    let handleSaveFail = useCallback(({ dtoOut: error }) => {
      setInitWorkspaceError(error);
    }, []);
    //@@viewOff:private

    //@@viewOn:interface
    //@@viewOff:interface

    //@@viewOn:render
    let child;
    let attrs = UU5.Common.VisualComponent.getAttrs(props, CLASS_NAMES.main());

    if (viewState === "error") {
      child = (
        <Plus4U5.App.SpaError error={data.dtoOut} errorData={data?.dtoOut?.uuAppErrorMap}>
          <UU5.Bricks.Lsi lsi={Lsi.notAuthorized} />
        </Plus4U5.App.SpaError>
      );
    } else if (viewState === "load") {
      child = <UU5.Bricks.Loading />;
    } else {
      if (Array.isArray(data.authorizedProfileList) && data.authorizedProfileList.length > 0) {
        child = (
          <UU5.Forms.ContextSection
            {...attrs}
            header={
              <UU5.Forms.ContextHeader
                content={<UU5.Bricks.Lsi lsi={Lsi.formHeader} />}
                info={<UU5.Bricks.Lsi lsi={Lsi.formHeaderInfo} />}
              />
            }
          >
            <UU5.Forms.ContextForm
              ref_={formRef}
              onSave={handleSave}
              onSaveDone={handleSaveDone}
              onSaveFail={handleSaveFail}
              controlled={false}
              inputColWidth={"m-12"}
              labelColWidth={"m-12"}
            >
              <UU5.Forms.Text
                required
                name="uuBtLocationUri"
                label={routeLsi.uuBtLocationUriLabel}
                tooltip={routeLsi.uuBtLocationUriTooltip}
                controlled={false}
              />
              <UU5.Forms.Text name="name" label={routeLsi.nameLabel} controlled={false} />
              <UU5.Forms.ContextControls
                buttonSubmitProps={{ content: <UU5.Bricks.Lsi lsi={Lsi.initialize} /> }}
                buttonCancelProps={{ className: CLASS_NAMES.cancelButton() }}
              />
            </UU5.Forms.ContextForm>

            {initWorkspaceError ? (
              initWorkspaceError.dtoOut ? (
                <UU5.Common.Error errorData={initWorkspaceError.dtoOut} />
              ) : (
                <UU5.Common.Error error={initWorkspaceError} moreInfo />
              )
            ) : null}
          </UU5.Forms.ContextSection>
        );
      } else {
        child = (
          <SpaUnauthorizedInit>
            <UU5.Bricks.Lsi lsi={Lsi.notAuthorizedForInit} />
          </SpaUnauthorizedInit>
        );
      }
    }
    return (
      <UU5.Common.Fragment>
        <Plus4U5.App.ArtifactSetter territoryBaseUri="" artifactId="" />
        {child}
      </UU5.Common.Fragment>
    );
  },
  //@@viewOff:render
});

export default InitAppWorkspace;
