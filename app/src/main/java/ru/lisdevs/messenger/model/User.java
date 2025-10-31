package ru.lisdevs.messenger.model;

public class User {
    public long id;
    public String name;
    public int age;
    public String city;
    public String description;

    public User(long id, String name, int age, String city, String description) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.city = city;
        this.description = description;
    }
}