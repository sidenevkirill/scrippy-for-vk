package ru.lisdevs.messenger.model;

public class UserInfo {
    public String id;
    public String firstName;
    public String lastName;
    public String bdate;
    public String screenName;
    public String sex;

    public UserInfo(String id, String firstName, String lastName, String bdate, String screenName, String sex) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.bdate = bdate;
        this.screenName = screenName;
        this.sex = sex;
    }
}
