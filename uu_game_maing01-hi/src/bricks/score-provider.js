//@@viewOn:imports
import {createComponent, useDataList} from "uu5g04-hooks";
import Calls from "calls";
import Config from "./config/config";
//@@viewOff:imports

const ScoreProvider = createComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "ScoreProvider",
  //@@viewOff:statics

  //@@viewOn:propTypes
  propTypes: {},
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  defaultProps: {},
  //@@viewOff:defaultProps

  render({children}) {
    //@@viewOn:hooks
    const pageInfo = {
      pageIndex: 0,
      pageSize: 20,
    };
    let listDataValues = useDataList({
      handlerMap: {
        load: () => Calls.scoreList({pageInfo}),
      },
    });


    let {state, data, newData, pendingData, errorData, handlerMap} = listDataValues;
    //@@viewOff:hooks

    //@@viewOn:render
    return children({
      state,
      data,
      newData,
      pendingData,
      errorData,
      handlerMap,
    });
    //@@viewOff:render
  },
});

export default ScoreProvider;
