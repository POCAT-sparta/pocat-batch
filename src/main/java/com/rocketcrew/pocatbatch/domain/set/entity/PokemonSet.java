package com.rocketcrew.pocatbatch.domain.set.entity;

import com.rocketcrew.pocatbatch.domain.freepost.entity.BaseEntity;
import com.rocketcrew.pocatbatch.domain.series.entity.Series;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@SQLDelete(sql = "UPDATE pokemon_sets SET deleted_at = NOW() WHERE id = ?")
@Table(name = "pokemon_sets",
        uniqueConstraints = @UniqueConstraint(columnNames = "set_id"))
public class PokemonSet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private Series series;

    @Column(name = "set_id", nullable = false, length = 50)
    private String setId;     // "swsh5" (TCGdex ID)

    @Column(name = "name", nullable = false, length = 100)
    private String name;      // "Rebel Clash"

    @Column(name = "name_ko", length = 1000)
    private String nameKo;    // "반역크래시" (공백 구분 한글 별칭)

    public void updateNameKo(String nameKo) {
        this.nameKo = nameKo;
    }
}
