package run_ffmpeg;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Provide common methods/services for this application.
 * @author Dan
 */
public class Common
{
	static Logger setupLogger( final String logFileName )
	{
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tF %1$tT] [%4$-7s] %5$s %n");
		Logger log = Logger.getLogger( BuildMovieAndShowIndex.class.getName() ) ;
		try
		{
			FileHandler logFileHandler = new FileHandler( logFileName ) ;
			logFileHandler.setFormatter( new SimpleFormatter() );
			log.addHandler( logFileHandler ) ;
			log.addHandler( new ConsoleHandler() ) ;
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
