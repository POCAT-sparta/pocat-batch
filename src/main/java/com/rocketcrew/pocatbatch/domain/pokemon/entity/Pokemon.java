package com.rocketcrew.pocatbatch.domain.pokemon.entity;

import com.rocketcrew.pocatbatch.domain.freepost.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@SQLDelete(sql = "UPDATE pokemon SET deleted_at = NOW() WHERE id = ?")
@Table(name = "pokemon",
        uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Pokemon extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;     // "Charizard"

    @Column(name = "name_ko", length = 100)
    private String nameKo;   // "리자몽"

    public void updateNameKo(String nameKo) {
        this.nameKo = nameKo;
    }
}
