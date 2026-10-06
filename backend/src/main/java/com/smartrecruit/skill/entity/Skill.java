package com.smartrecruit.skill.entity;

import com.smartrecruit.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Référentiel de compétences partagé par les profils candidats, les CV et les offres. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "skills")
public class Skill extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 50)
    private String category;

    public Skill(String name, String category) {
        this.name = name;
        this.category = category;
    }
}
