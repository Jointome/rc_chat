import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;


enum State {
	  INIT, OUTSIDE, INSIDE
	}

public class CUser implements Comparable<CUser>{
    private String nickname;
    private State st;
    private CRoom room;
    private SocketChannel sc;
    
	public CUser(String nickname, State st, CRoom room, SocketChannel sc) {
		super();
		this.nickname = nickname;
		this.st = st;
		this.room = room;
		this.sc = sc;
	}
	
	public String getNickname() {
		return nickname;
	}
	
	public void setNickname(String nickname) {
		this.nickname = nickname;
	}
	
	public State getSt() {
		return st;
	}
	
	public void setSt(State st) {
		this.st = st;
	}
	
	public CRoom getRoom() {
		return room;
	}
	
	public void setRoom(CRoom room) {
		this.room = room;
	}
	
	public SocketChannel getSc() {
		return sc;
	}
	
	public void setSc(SocketChannel sc) {
		this.sc = sc;
	}

	@Override
	  public int compareTo(CUser other){
	    return this.nickname.compareTo(other.nickname);
	  }


}