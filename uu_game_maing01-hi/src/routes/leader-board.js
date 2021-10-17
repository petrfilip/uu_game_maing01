//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent} from "uu5g04-hooks";
import Config from "./config/config";
import ScoreProvider from "../bricks/score-provider";
//@@viewOff:imports

const LeaderBoard = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Game",
  //@@viewOff:LeaderBoard

  render(props) {
    //@@viewOn:hooks
    //@viewOff:hooks

    //@@viewOn:private

    function renderReady(items) {
      return (
        <>
          {JSON.stringify(items)}
        </>
      );
    }

    function renderError(errorData) {
      switch (errorData.operation) {
        case "load":
        case "loadNext":
        default:
          return (
            <UU5.Bricks.Error
              content={<UU5.Bricks.Lsi lsi={"Error"}/>}
              error={errorData.error}
              errorData={errorData.data}
            />
          );
      }
    }

    //@@viewOff:private

    //@@viewOn:render
    return (
      <UU5.Bricks.Container>
        Leader Board

        <ScoreProvider>
          {({state, data, errorData, handlerMap}) => {
            switch (state) {
              case "pending":
              case "pendingNoData":
                return <UU5.Bricks.Loading/>;
              case "error":
              case "errorNoData":
                return renderError(errorData);
              case "itemPending":
              case "ready":
              case "readyNoData":
              default:
                return renderReady(data);
            }
          }}
        </ScoreProvider>

      </UU5.Bricks.Container>
    );
    //@@viewOff:render
  },
});

export default LeaderBoard;
