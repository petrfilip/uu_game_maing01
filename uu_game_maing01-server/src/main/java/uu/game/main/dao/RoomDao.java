package uu.game.main.dao;

import java.util.List;
import org.springframework.data.domain.Pageable;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.datastore.exceptions.DatastoreConcurrencyRuntimeException;
import uu.app.datastore.exceptions.DatastoreLimitsRuntimeException;
import uu.app.datastore.exceptions.DatastoreRuntimeException;
import uu.app.datastore.exceptions.DatastoreUnexpectedRuntimeException;
import uu.game.main.abl.dto.Room;

public interface RoomDao {

  Room create(Room room);

  List<Room> create(List<Room> list) throws DatastoreRuntimeException, DatastoreLimitsRuntimeException;

  boolean exists(String awid, String id) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException;

  Room get(String awid, String id) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException;

  long getCount(String awid) throws DatastoreRuntimeException;

  Room update(Room entity) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException, DatastoreConcurrencyRuntimeException;

  void delete(String awid, String id) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException, DatastoreConcurrencyRuntimeException;

  void delete(Room entity) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException, DatastoreConcurrencyRuntimeException;

  List<Room> list(String awid, Pageable pageable) throws DatastoreRuntimeException;

  PagedResult<Room> list(String awid, PageInfo pageInfo) throws DatastoreRuntimeException;
}
