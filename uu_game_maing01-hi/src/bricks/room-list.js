//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent, useState} from "uu5g04-hooks";
import Config from "./config/config";

//@@viewOff:imports

const viewListRowClassName = Config.Css.css`
      cursor: crosshair;
  `;

function handleAddList(onCreate) {
  return async (opt) => {
    const result = await onCreate({roomName: opt.value});
    UU5.Environment.getRouter().setRoute("game", {roomId: result.id});
  };
}

function handleEditList(onDelete, list) {
  return async (opt) => {
    await onDelete({id: list.id, forceDelete: true});
    UU5.Environment.getRouter().setRoute("home");
  };
}

const RoomList = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "RoomList",
  //@@viewOff:statics

  //@@viewOn:propTypes
  propTypes: {
    list: UU5.PropTypes.any,
  },
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  defaultProps: {
    list: null,
  },
  //@@viewOff:defaultProps

  render({list, onDelete, onUpdate, onCreate}) {
    //@@viewOn:hooks
    const [editMode, setEditMode] = useState(false);
    //@@viewOff:hooks

    const addList = (
      <UU5.Forms.TextButton
        value={list?.roomName || ""}
        message={"Add"}
        buttons={[
          {
            icon: "mdi-check",
            onClick: handleAddList(onCreate),
            colorSchema: "info",
          },
        ]}
      />
    );

    const editList = (
      <UU5.Forms.TextButton
        placeholder="Insert room name"
        value={list?.roomName || ""}
        message="Room  name"
        buttons={[
          {
            icon: "mdi-delete",
            onClick: handleEditList(onDelete, list),
            colorSchema: "info",
          },

          {
            icon: "mdi-check",
            onClick: (opt) => {
              if (opt.value !== list.roomName) {
                onUpdate({id: list.id, name: opt.value});
              }
              setEditMode(false);
            },
            colorSchema: "info",
          },
        ]}
      />
    );

    const viewList = (
      <UU5.Bricks.Row className={viewListRowClassName}>
        <UU5.Bricks.Column colWidth="xs-12 s-10">{list?.roomName || ""}</UU5.Bricks.Column>
        <UU5.Bricks.Column colWidth="xs-12 s-2">
          <UU5.Bricks.Button icon="mdi-lead-pencil" onClick={() => setEditMode(true)}>
            <UU5.Bricks.Icon icon="mdi-lead-pencil"/>
          </UU5.Bricks.Button>
        </UU5.Bricks.Column>
      </UU5.Bricks.Row>
    );

    //@@viewOn:render
    return (!list && addList) || (editMode && editList) || viewList;
    //@@viewOff:render
  },
});

export default RoomList;
