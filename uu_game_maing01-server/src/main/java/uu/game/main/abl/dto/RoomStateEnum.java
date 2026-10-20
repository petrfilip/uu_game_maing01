package uu.game.main.abl.dto;

public enum RoomStateEnum {
  WAITING,
  ACTIVE,
  /** Deleted, or reclaimed after being abandoned. Published once so clients can stop watching. */
  CLOSED
}
