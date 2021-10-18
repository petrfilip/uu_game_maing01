//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent} from "uu5g04-hooks";
import Config from "./config/config";
import ScoreProvider from "../bricks/score-provider";
import PlayerScoreItem from "../bricks/player-score-item";
import Uu5Tiles from "uu5tilesg02";

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
        <Uu5Tiles.Grid
          data={items}
          tileHeight="auto"
          tileMinWidth={200}
          tileMaxWidth={400}
          tileSpacing={8}
          rowSpacing={8}
        >
          {PlayerScoreItem}
        </Uu5Tiles.Grid>
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
      <UU5.Bricks.Section level={1} header="Leader board" className={UU5.Common.Css.css`padding: 16px`}>

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

      </UU5.Bricks.Section>
    );
    //@@viewOff:render
  },
});

export default LeaderBoard;
