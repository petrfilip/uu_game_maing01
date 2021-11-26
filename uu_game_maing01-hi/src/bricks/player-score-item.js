import UU5 from "uu5g04";
import "uu5g04-bricks";
import "uu5g04-forms";
import {createVisualComponent} from "uu5g04-hooks";

const PlayerScoreItem = createVisualComponent({
  //@@viewOn:statics
  displayName: "Uu5TilesDemo.CustomTile",
  //@@viewOff: statics

  //@@viewOn:propTypes
  //@@viewOff:propTypes

  //@@viewOn:getDefaultProps
  //@@viewOff:getDefaultProps

  render(props) {
    let {data} = props;

    //@@viewOn:hooks
    //@@viewOff:hooks

    //@@viewOn:interface
    //@@viewOff:interface

    //@@viewOn:private
    const classNames = {
      main: UU5.Common.Css.css`
        height: 100%;
        border: 1px solid rgb(189, 189, 189);
        border-radius: 4px;
        `,
      divContainer: UU5.Common.Css.css`
        position: relative;
        width: 100%;
        height: 100%;
        overflow: hidden;
        padding: 8px;
        display: flex;
        flex-direction: column;
        `
    };

    let mainAttrs = UU5.Common.VisualComponent.getAttrs(props);
    mainAttrs.className = (mainAttrs.className + " " || "") + classNames.main;
    //@@viewOff:private

    //@@viewOn:render
    return (
      <div {...mainAttrs}>
        <div className={classNames.divContainer} >
          {/*<div className={UU5.Common.Css.css`padding-bottom: 8px`}>*/}
          {/*  <strong className={UU5.Common.Css.css`margin-right: 8px`}>*/}
          {/*    <UU5.Bricks.Lsi lsi={data.data.playerName}/>*/}
          {/*  </strong>*/}
          {/*  <Plus4U5.Bricks.BusinessCard visual="micro" elevation="0" elevationHover="1" uuIdentity={data.data.uuIdentity}/>*/}
          {/*</div>*/}
          <div>
            {`Player: ${data.data.uuIdentity}`}
          </div>
          <div>
            {`Score: ${data.data.score}`}
          </div>
        </div>
      </div>
    );
    //@@viewOff:render
  }
});

export default PlayerScoreItem
