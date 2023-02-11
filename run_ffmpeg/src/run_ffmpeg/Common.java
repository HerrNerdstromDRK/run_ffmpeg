package run_ffmpeg;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provide common methods/services for this application.
 * @author Dan
 */
public class Common
{
	static Logger setupLogger( final String logFileName, final String className )
	{
		Logger log = Logger.getLogger( className ) ;
		try
		{
			// Disable default handlers
			log.setUseParentHandlers( false ) ;
			FileHandler logFileHandler = new FileHandler( logFileName ) ;
			logFileHandler.setFormatter( new MyLogFormatter() );
			log.addHandler( logFileHandler ) ;
			
			ConsoleHandler ch = new ConsoleHandler() ;
			ch.setFormatter( new MyLogFormatter() ) ;
			log.addHandler( ch ) ;
		}
		catch( Exception theException )
		{
			System.out.println( "BuildMovieAndShowIndex> Unable to create logger FileHandler as file "
					+ logFileName
					+ ": " + theException ) ;
		}
		log.setLevel( Level.ALL ) ;
		System.out.println( "setupLogger> Established logger with log filename: " + logFileName ) ;
		return log ;
	}

}
