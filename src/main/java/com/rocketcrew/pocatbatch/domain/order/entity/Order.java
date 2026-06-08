package com.rocketcrew.pocatbatch.domain.order.entity;

import com.rocketcrew.pocatbatch.domain.freepost.entity.BaseEntity;
import com.rocketcrew.pocatbatch.domain.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "orders")
@SQLDelete(sql = "UPDATE orders SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Order extends BaseEntity {

    @Column(name = "order_uid", nullable = false, length = 50)
    private String orderUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    public void completeOrder() {
        this.status = OrderStatus.ORDER_COMPLETED;
    }
}
