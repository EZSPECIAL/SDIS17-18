public class Client {

	/**
	 * Receives data to register or lookup a license plate.
	 * 
	 * Format: <host_name> <port_number> <oper> <opnd>*
	 * 
	 * @param args client arguments for communicating with server
	 */
	public static void main(String[] args) {
		
		// TODO Format debug info
		for(String arg : args) {
			System.out.print("arg: ");
			System.out.println(arg);
		}

		// Parse register operation
		if(args[2].compareTo("register") == 0) {
			
			String[] result = args[3].split(":");
			for(String values : result) {
				
				System.out.print("operands: ");
				System.out.println(values);
			}
		}
		
		
	}

}