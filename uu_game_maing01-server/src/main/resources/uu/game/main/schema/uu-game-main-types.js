/* eslint-disable */


const gameMainInitDtoInType = shape({
  authoritiesUri: uri().isRequired()
});


// custom types

const pageInfoType = shape({
  pageIndex: integer(), // index of the page
  pageSize: integer() // max count per page
});


const roomCreateDtoInType = shape({
  roomName: string(1, null).isRequired(),
  roomOwner: uuIdentity().isRequired()
});

const roomJoinDtoInType = shape({
  roomId: string(1, null).isRequired(),
  playerId: uuIdentity().isRequired()
});

const scoreGetByUuIdentityDtoInType = shape({
  uuIdentity: uuIdentity().isRequired()
});

const scoreListDtoInType = shape({
  pageInfo: pageInfoType(),
});


