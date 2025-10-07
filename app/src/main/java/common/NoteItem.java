package common;

public class NoteItem {
	public String comment_id;
    public String comment_timestamp;
    public String driver_name;
    public String user_id;
    public String shipment_id;
    public String comment;

    public boolean isMyNote(){
        return App.CurrentUser != null && user_id.equalsIgnoreCase(String.valueOf(App.CurrentUser.user_id));
    }
}
