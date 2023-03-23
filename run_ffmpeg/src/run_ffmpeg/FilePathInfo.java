package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilePathInfo
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_filepathinfo.txt" ;
	
	public FilePathInfo()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}
	
	public static void main( String[] args )
	{
		FilePathInfo fpi = new FilePathInfo() ;
		fpi.run() ;
	}
	
	public void run()
	{
		List< String > fileNames = new ArrayList< >() ;
		fileNames.add( "\\\\yoda\\Backup\\Movies\\Transformers (2007)\\Transformers (2007).mkv" ) ;
		fileNames.add( "\\\\yoda\\Backup\\Movies\\Transformers (2007)" ) ;

		try
		{
//			Pattern yearPattern = Pattern.compile( "^[1-9]\\d*$");
			Pattern yearPattern = Pattern.compile( "\\b\\d{4}\\b");
			String[] testYearStrings = {
					"Name (1999)", // Pass
					"1999", // Fail
					"Name 1999", // Fail
					"(2)", // Fail
					"Name - Name2 (2000)", // Pass
					"Name (123)", // Fail
					"Name (2022)" // Pass

			};
			for( String testYear : testYearStrings )
			{
				Matcher theMatcher = yearPattern.matcher( testYear ) ;
				log.info( "regex> Input: \"" + testYear + "\": matches(): " + theMatcher.matches() ) ;
			}

			for( String fileName : fileNames )
			{
				File theFile = new File( fileName ) ;
				log.info( "**********" ) ;
				log.info( "main> File: " + theFile ) ;
				log.info( "main> length(): " + theFile.length() ) ;
				log.info( "main> getCanonicalPath(): " + theFile.getCanonicalPath() ) ;
				log.info( "main> getAbsolutePath(): " + theFile.getAbsolutePath() ) ;
				log.info( "main> getName(): " + theFile.getName() ) ;
				log.info( "main> getPath(): " + theFile.getPath() ) ;
				log.info( "main> getParent(): " + theFile.getParent() ) ;
				log.info( "main> getParentFile().getName(): " + theFile.getParentFile().getName() ) ;
				log.info( "main> getParentFile().getParent(): " + theFile.getParentFile().getParent() ) ;
				log.info( "main> getPath(): " + theFile.getPath() ) ;
			}

			final List< String > allMP4Drives = common.getAllMP4Drives() ;
			for( String mp4Drive : allMP4Drives )
			{
				final File mp4DriveFile = new File( mp4Drive ) ;
				final double freeSpace = mp4DriveFile.getFreeSpace() / (1024.0 * 1024 * 1024) ;
				log.info( "Free space on " + mp4Drive + ": " + common.getNumberFormat().format( freeSpace ) + "GB" ) ;
			}
			
		}
		catch( Exception theException )
		{
			log.warning( "main> Exception: " + theException ) ;
			theException.printStackTrace() ;
		}
	} //main()

}
