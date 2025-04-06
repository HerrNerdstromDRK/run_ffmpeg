package run_ffmpeg;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Logger;

public class RMI_Transcode_Server
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_rmi_transcode_server.txt" ;

	public RMI_Transcode_Server()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		try
		{
			(new RMI_Transcode_Server()).execute() ;
		}
		catch( Exception theException )
		{
			System.out.println( "main> Exception: " + theException.toString() ) ;
			theException.printStackTrace() ;
		}
	}

	public void execute()
	{
		try
		{
			RMI_Transcode_Server_Implementation transcodeServerImpl = new RMI_Transcode_Server_Implementation() ;
			Registry registry = LocateRegistry.createRegistry( 12345 ) ;
			registry.bind( "RMI_Transcode_Server", transcodeServerImpl ) ;
			log.info( "Server is running..." ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}
	

}
