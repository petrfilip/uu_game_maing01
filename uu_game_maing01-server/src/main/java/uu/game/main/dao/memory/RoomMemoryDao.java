package uu.game.main.dao.memory;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uu.app.datastore.domain.PageInfo;
import uu.app.datastore.domain.PagedResult;
import uu.app.datastore.exceptions.DatastoreConcurrencyRuntimeException;
import uu.app.datastore.exceptions.DatastoreLimitsRuntimeException;
import uu.app.datastore.exceptions.DatastoreRuntimeException;
import uu.app.datastore.exceptions.DatastoreUnexpectedRuntimeException;
import uu.game.main.abl.dto.Room;
import uu.game.main.dao.RoomDao;

@Component
public class RoomMemoryDao implements RoomDao {

  private final Map<String, Map<String, Room>> rooms = new HashMap<>();


  @Override
  public boolean exists(String awid, String id) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException {
    Map<String, Room> idRoomMap = rooms.getOrDefault(awid, Collections.emptyMap());
    return idRoomMap.containsKey(id);
  }

  // STANDARD UAF METHODS

  @Override
  public Room create(Room entity) throws DatastoreRuntimeException, DatastoreLimitsRuntimeException {
    entity.setId(generatedIdentifier());
    entity.getSys().setCts(ZonedDateTime.now());
    entity.getSys().setMts(ZonedDateTime.now());
    entity.getSys().setRevision(0);
    Map<String, Room> roomByAwid = rooms.getOrDefault(entity.getAwid(), new HashMap<>());
    roomByAwid.put(entity.getId(), entity);
    rooms.put(entity.getAwid(), roomByAwid);
    return entity;
  }

  @Override
  public List<Room> create(List<Room> list) throws DatastoreRuntimeException, DatastoreLimitsRuntimeException {
    return list.stream().map(this::create).collect(Collectors.toList());
  }

  @Override
  public Room get(String awid, String id) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException {
    Map<String, Room> idRoomMap = rooms.getOrDefault(awid, Collections.emptyMap());
    return idRoomMap.get(id);
  }

  @Override
  public long getCount(String awid) throws DatastoreRuntimeException {
    return list(awid, new PageRequest(0, Integer.MAX_VALUE)).size();
  }

  @Override
  public Room update(Room entity) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException, DatastoreConcurrencyRuntimeException {
    Map<String, Room> idRoomMap = rooms.get(entity.getAwid());
    entity.getSys().setMts(ZonedDateTime.now());
    entity.getSys().setRevision(entity.getSys().getRevision() + 1);
    idRoomMap.put(entity.getId(), entity);
    return entity;
  }

  @Override
  public void delete(String awid, String id) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException, DatastoreConcurrencyRuntimeException {
    Map<String, Room> idRoomMap = rooms.getOrDefault(awid, Collections.emptyMap());
    idRoomMap.remove(id);
  }

  @Override
  public void delete(Room entity) throws DatastoreRuntimeException, DatastoreUnexpectedRuntimeException, DatastoreConcurrencyRuntimeException {
    delete(entity.getAwid(), entity.getId());
  }

  @Override
  public List<Room> list(String awid, Pageable pageable) throws DatastoreRuntimeException {
    Map<String, Room> idRoomMap = rooms.getOrDefault(awid, Collections.emptyMap());
    return new ArrayList<>(idRoomMap.values());
  }

  @Override
  public PagedResult<Room> list(String awid, PageInfo pageInfo) throws DatastoreRuntimeException {
    Pageable page = new PageRequest(pageInfo.getPageIndex(), pageInfo.getPageSize());

    List<Room> list = list(awid, page);
    pageInfo.setTotal(list.size());
    return new PagedResult(list, pageInfo);
  }

  private String generatedIdentifier() {
    return UUID.randomUUID().toString().replaceAll("-", "");
  }

}
