package org.folio.services.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.CopilotGenerated;
import org.folio.dao.inventory.impl.HoldingsEventDao;
import org.folio.dao.inventory.impl.InstanceEventDao;
import org.folio.dao.inventory.impl.InventoryEventDaoImpl;
import org.folio.dao.inventory.impl.ItemEventDao;
import org.folio.mapper.InventoryEntitiesToAuditCollectionMapper;
import org.folio.mapper.InventoryEventToEntityMapper;
import org.folio.rest.jaxrs.model.InventoryAuditCollection;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.inventory.InventoryEventService;
import org.folio.util.inventory.InventoryEvent;
import org.folio.util.inventory.InventoryEventType;
import org.folio.util.inventory.InventoryResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@CopilotGenerated(partiallyGenerated = true)
@ExtendWith(MockitoExtension.class)
public class InventoryEventServiceImplTest {

  private final Map<InventoryResourceType, InventoryEventDaoImpl> daos = new EnumMap<>(InventoryResourceType.class);

  @Mock
  private RowSet<Row> rowSet;
  @Mock
  private InstanceEventDao instanceEventDao;
  @Mock
  private HoldingsEventDao holdingsEventDao;
  @Mock
  private ItemEventDao itemEventDao;
  @Mock
  private InventoryEventToEntityMapper eventToEntityMapper;
  @Mock
  private InventoryEntitiesToAuditCollectionMapper entitiesToAuditCollectionMapper;
  @Mock
  private ConfigurationService configurationService;

  private InventoryEventService eventService;

  @BeforeEach
  public void setUp() {
    daos.put(InventoryResourceType.INSTANCE, instanceEventDao);
    daos.put(InventoryResourceType.HOLDINGS, holdingsEventDao);
    daos.put(InventoryResourceType.ITEM, itemEventDao);

    daos.values().forEach(dao -> doCallRealMethod().when(dao).resourceType());

    eventService = new InventoryEventServiceImpl(eventToEntityMapper, entitiesToAuditCollectionMapper,
      configurationService, List.of(instanceEventDao, holdingsEventDao, itemEventDao));
  }

  @EnumSource(value = InventoryResourceType.class, mode = EnumSource.Mode.EXCLUDE, names = {"UNKNOWN"})
  @ParameterizedTest
  void shouldSaveInventoryRecordSuccessfully(InventoryResourceType resourceType) {
    var dao = daos.get(resourceType);
    var inventoryEvent = createInventoryEvent(resourceType);
    doReturn(Future.succeededFuture(rowSet)).when(dao).save(any(), anyString());

    var saveFuture = eventService.saveEvent(inventoryEvent, "testTenant");
    saveFuture.onComplete(asyncResult -> assertTrue(asyncResult.succeeded()));

    verify(dao, times(1)).save(any(), anyString());
  }

  @Test
  void shouldFailToSaveEventWhenDaoNotFound() {
    var inventoryEvent = createInventoryEvent(InventoryResourceType.UNKNOWN);

    var saveFuture = eventService.saveEvent(inventoryEvent, "testTenant");
    saveFuture.onComplete(asyncResult -> assertTrue(asyncResult.failed()));

    verify(instanceEventDao, times(0)).save(any(), anyString());
  }

  @ParameterizedTest
  @EnumSource(value = InventoryResourceType.class, mode = EnumSource.Mode.EXCLUDE, names = {"UNKNOWN"})
  void shouldRetrieveEventsSuccessfully(InventoryResourceType resourceType) {
    var dao = daos.get(resourceType);
    var entityId = UUID.randomUUID().toString();
    var eventDate = "1672531200000";
    var tenantId = "testTenant";
    var inventoryAuditCollection = new InventoryAuditCollection();
    var entityUUID = UUID.fromString(entityId);
    var eventDateTime = new Timestamp(Long.parseLong(eventDate));
    var setting = new Setting().withValue(10);

    doReturn(Future.succeededFuture(setting)).when(configurationService).getSetting(
      org.folio.services.configuration.Setting.INVENTORY_RECORDS_PAGE_SIZE, tenantId);
    doReturn(Future.succeededFuture(List.of())).when(dao).get(entityUUID, eventDateTime, 10, tenantId);
    doReturn(inventoryAuditCollection).when(entitiesToAuditCollectionMapper).apply(anyList());

    var getFuture = eventService.getEvents(resourceType, entityId, eventDate, tenantId);
    getFuture.onComplete(asyncResult -> assertTrue(asyncResult.succeeded()));

    verify(dao).get(entityUUID, eventDateTime, 10, tenantId);
  }

  @Test
  void shouldFailToRetrieveEventsWhenEntityIdIsInvalid() {
    var resourceType = InventoryResourceType.INSTANCE;
    var invalidEntityId = "invalid-uuid";
    var validEventDate = "1672531200000";
    var tenantId = "testTenant";

    var getFuture = eventService.getEvents(resourceType, invalidEntityId, validEventDate, tenantId);
    getFuture.onComplete(asyncResult -> assertTrue(asyncResult.failed()));

    verify(instanceEventDao, never()).get(any(UUID.class), any(Timestamp.class), anyInt(), anyString());
  }

  @Test
  void shouldFailToRetrieveEventsWhenEventDateIsInvalid() {
    var resourceType = InventoryResourceType.INSTANCE;
    var validEntityId = UUID.randomUUID().toString();
    var invalidEventDate = "invalid-date";
    var tenantId = "testTenant";

    var getFuture = eventService.getEvents(resourceType, validEntityId, invalidEventDate, tenantId);
    getFuture.onComplete(asyncResult -> assertTrue(asyncResult.failed()));

    verify(instanceEventDao, never()).get(any(UUID.class), any(Timestamp.class), anyInt(), anyString());
  }

  @Test
  void shouldFailToRetrieveEventsWhenDaoNotFound() {
    var resourceType = InventoryResourceType.UNKNOWN;
    var entityId = UUID.randomUUID().toString();
    var eventDate = "1672531200000";
    var tenantId = "testTenant";

    var getFuture = eventService.getEvents(resourceType, entityId, eventDate, tenantId);
    getFuture.onComplete(asyncResult -> assertTrue(asyncResult.failed()));

    verify(instanceEventDao, never()).get(any(UUID.class), any(Timestamp.class), anyInt(), anyString());
  }

  private InventoryEvent createInventoryEvent(InventoryResourceType resourceType) {
    var inventoryEvent = new InventoryEvent();
    inventoryEvent.setEventId(UUID.randomUUID().toString());
    inventoryEvent.setEntityId(UUID.randomUUID().toString());
    inventoryEvent.setResourceType(resourceType);
    inventoryEvent.setEventTs(System.currentTimeMillis());
    inventoryEvent.setType(InventoryEventType.CREATE);
    return inventoryEvent;
  }
}