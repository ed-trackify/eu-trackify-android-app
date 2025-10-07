package common;

public class UserRef {
	public String user;
	public String authenticated;
	public int user_type;
	public int user_id;
	public String auth_key;
	public String error_message;

	public enum UserType {
		None, Distributor, Driver // , WarehouseManager, WarehouseAdmin, Packer
	}

	public UserType GetUserType() {
		if (user_type == 1)
			return UserType.Distributor;
		else if (user_type == 2)
			return UserType.Driver;
		// else if (user_type == 2)
		// return UserType.WarehouseManager;
		// else if (user_type == 3)
		// return UserType.WarehouseAdmin;
		// else if (user_type == 4)
		// return UserType.Packer;
		else
			return UserType.None;
	}
}
