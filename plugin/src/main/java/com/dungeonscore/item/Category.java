package com.dungeonscore.item;

public enum Category {
    WEAPON("Weapon"),
    BOW("Bow"),
    STAFF("Staff"),
    ARMOR("Armor"),
    CONSUMABLE("Consumable"),
    ARTIFACT("Artifact"),
    MATERIAL("Material"),
    CROWN("Boss Crown"),
    ICON("Icon");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
