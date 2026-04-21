package com.changelog.model.cc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity(name = "TenantSlug")
@Table(name = "tenants", schema = "cc")
@Getter
@Setter
public class Tenant {
    @Id
    private UUID id;
    private String slug;
}
