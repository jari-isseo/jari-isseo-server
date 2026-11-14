package com.jariisseo.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 변경이 DB 에 커밋된 뒤에만 손님 화면에 보낸다
//   - 저장이 실패해 롤백되면 이벤트도 나가지 않음 (손님 화면에 없는 변경이 보이지 않도록)
//   - 커밋 전에 보내면, 이벤트를 받은 화면이 스냅샷을 다시 읽었을 때 아직 옛 값이 보일 수 있음
@Component
public class StoreChangedListener {

    private final StoreEventHub hub;

    public StoreChangedListener(StoreEventHub hub) {
        this.hub = hub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(StoreChangedEvent event) {
        hub.broadcast(event.storeId(), event.name(), event.data());
    }
}
