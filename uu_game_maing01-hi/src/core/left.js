//@@viewOn:imports
import UU5 from "uu5g04";
import "uu5g04-bricks";
import {createVisualComponent, useDataList} from "uu5g04-hooks";
import Plus4U5 from "uu_plus4u5g01";
import "uu_plus4u5g01-app";

import Config from "./config/config.js";
import Lsi from "../config/lsi.js";
import RoomList from "../bricks/room-list";
import Calls from "../calls";

//@@viewOff:imports

const STATICS = {
  //@@viewOn:static
  displayName: Config.TAG + "Left",
  //@@viewOff:static
};

export const Left = createVisualComponent({
  ...STATICS,

  //@@viewOn:propTypes
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  //@@viewOff:defaultProps

  render(props) {

    //@@viewOn:hooks
    let listDataValues = useDataList({
      pageSize: 335,
      handlerMap: {
        load: Calls.roomList,
        createList: Calls.roomCreate,
        deleteList: Calls.roomDelete,
      },
    });

    let {state, data, newData, pendingData, errorData, handlerMap} = listDataValues;
    //@@viewOff:hooks

    //@@viewOn:private
    function getListComponent(item) {
      return (
        <RoomList
          key={item?.id}
          list={item}
          onDelete={handlerMap.deleteList}
          onCreate={handlerMap.createList}
        />
      );
    }

    const menuItems =
      (state === "ready" &&
        data &&
        data.map((item) => ({
          id: item.data.id,
          href: "game?roomId=" + item.data.id,
          content: getListComponent(item.data),
        }))) ||
      [];
    menuItems.push({id: "new", href: "#", content: getListComponent()});
    //@@viewOff:private

    //@@viewOn:interface
    //@@viewOff:interface

    //@@viewOn:render
    return (
      <Plus4U5.App.Left
        {...props}
        logoProps={{
          backgroundColor: UU5.Environment.colors.blue.c700,
          backgroundColorTo: UU5.Environment.colors.blue.c500,
          title: "uuGame",
          companyLogo: Plus4U5.Environment.basePath + "assets/img/unicorn-logo.svg",
          generation: "1",
        }}
        aboutItems={[{content: <UU5.Bricks.Lsi lsi={Lsi.left.about}/>, href: "about"}]}
        helpHref={null}
      >

        <Plus4U5.App.MenuTree
          borderBottom
          // NOTE Item "id" equals to useCase so that item gets automatically selected when route changes (see spa-autheticated.js).
          items={[{id: "home", href: "home", content: <UU5.Bricks.Lsi lsi={Lsi.left.home}/>}]}
          // items={menuItems}

        />
        <Plus4U5.App.MenuTree
          borderBottom
          // NOTE Item "id" equals to useCase so that item gets automatically selected when route changes (see spa-autheticated.js).
          // items={[{id: "home", href: "home", content: <UU5.Bricks.Lsi lsi={Lsi.left.home}/>}]}
          items={menuItems}

        />

        <Plus4U5.App.MenuTree
          borderBottom
          // NOTE Item "id" equals to useCase so that item gets automatically selected when route changes (see spa-autheticated.js).
          items={[{id: "home", href: "leaderboard", content: "LeaderBoard"}]}
          // items={menuItems}

        />
      </Plus4U5.App.Left>
    );
    //@@viewOff:render
  },
});

export default Left;
