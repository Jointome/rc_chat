import java.util.*;


class CRoom {
  private Set<CUser> usersInRoom;
  private String name;

  public CRoom(String name) {
    this.usersInRoom = new TreeSet<CUser>();
    this.name = name;
  }

  public CUser[] getUserArray() {
    return this.usersInRoom.toArray(new CUser[this.usersInRoom.size()]);
  }

  public void joinUser(CUser user) {
    this.usersInRoom.add(user);
  }

  public void leftUser(CUser user) {
    this.usersInRoom.remove(user);
  }

  public String getName() {
    return this.name;
  }
}