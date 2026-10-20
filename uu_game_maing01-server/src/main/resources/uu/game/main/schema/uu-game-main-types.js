/* eslint-disable */

const gameMainInitDtoInType = shape({
  uuAppProfileAuthorities: uri().isRequired()
});


// custom types

const pageInfoType = shape({
  pageIndex: integer(), // index of the page
  pageSize: integer() // max count per page
});


const roomCreateDtoInType = shape({
  roomName: string(1, null).isRequired(),
  roomOwner: uuIdentity().isRequired(),
  gameName: oneOf(["soldat", "bulanci"]) // optional; falls back to game.defaultGame
});

const roomJoinDtoInType = shape({
  roomId: string(1, null).isRequired(),
  playerId: uuIdentity().isRequired()
});

// Either key identifies the room: every room command server side calls it roomId, while the uu5
// data list deletes an item by posting the item, whose identifier is id.
const roomDeleteDtoInType = shape({
  roomId: string(1, null),
  id: string(1, null),
  forceDelete: boolean()
});

const scoreGetByUuIdentityDtoInType = shape({
  uuIdentity: uuIdentity().isRequired()
});

const scoreListDtoInType = shape({
  pageInfo: pageInfoType(),
});


