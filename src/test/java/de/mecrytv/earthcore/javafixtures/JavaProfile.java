package de.mecrytv.earthcore.javafixtures;

import de.mecrytv.earthcore.database.annotations.Column;
import de.mecrytv.earthcore.database.annotations.JsonColumn;
import de.mecrytv.earthcore.database.annotations.PrimaryKey;
import de.mecrytv.earthcore.database.annotations.Table;

import java.util.List;
import java.util.UUID;

@Table("java_profiles")
public class JavaProfile {

    @PrimaryKey
    private UUID uuid;

    @Column(name = "last_known_name")
    private String name;

    private long coins;

    private boolean banned;

    @JsonColumn
    private List<String> purchases;

    private transient String ignoriert;

    public JavaProfile() {
    }

    public JavaProfile(UUID uuid, String name, long coins, boolean banned, List<String> purchases) {
        this.uuid = uuid;
        this.name = name;
        this.coins = coins;
        this.banned = banned;
        this.purchases = purchases;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public long getCoins() {
        return coins;
    }

    public boolean isBanned() {
        return banned;
    }

    public List<String> getPurchases() {
        return purchases;
    }

    public String getIgnoriert() {
        return ignoriert;
    }
}
