package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class MoveFileThreadAction extends ThreadAction
{
	public String sourceFileName = null ;
	public String destinationFileName = null ;
	private transient Logger log = null ;
	
	public boolean isTestMode()
	{
		return run_ffmpeg.testMode ;
	}

	public MoveFileThreadAction( String sourceFileName, String destinationFileName )
	{
		this.sourceFileName = sourceFileName ;
		this.destinationFileName = destinationFileName ;
		log = run_ffmpeg.getLogger() ;
	}

	@Override
	public void doAction()
	{
		log.info( toString() ) ;

		File sourceFile = new File( sourceFileName ) ;
		Path sourcePath = Paths.get( sourceFile.getParent() ) ;

		File destinationFile = new File( destinationFileName ) ;
		Path destinationPath = Paths.get( destinationFile.getParent() ) ;

		try
		{
			if( !Files.exists( sourcePath ) )
			{
				log.warning( "sourcePath does not exist: " + sourcePath
						+ " " + toString() ) ;
				if( !isTestMode() )
				{
					Files.createDirectory( sourcePath ) ;
				}
			}

			if( !Files.exists( destinationPath ) )
			{
				log.warning( "destinationPath does not exist: " + destinationPath
						+ " " + toString() ) ;
				if( !isTestMode() )
				{
					Files.createDirectory( destinationPath ) ;
				}
			}

			if( !isTestMode() )
			{
				final long startTime = System.nanoTime() ;
				Path temp = Files.move(
						Paths.get( sourceFileName ),
						Paths.get( destinationFileName ) ) ;
				if( temp != null )
				{
					final long endTime = System.nanoTime() ;
					final double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;
					final long fileLength = destinationFile.length() ;
					final double fileLengthInMB = fileLength / 1e6 ;
					final double MBPerSecond = fileLengthInMB / timeElapsedInSeconds ;
					
					log.info( "Success; Total elapsed time: "
			    			+ run_ffmpeg.numFormat.format( timeElapsedInSeconds )
			    			+ " seconds, "
			    			+ run_ffmpeg.numFormat.format( timeElapsedInSeconds / 60.0 )
			    			+ " minutes; moved " + fileLengthInMB + "MB at "
			    			+ run_ffmpeg.numFormat.format( MBPerSecond ) + "MB/sec"
			    			+ " " + toString() ) ;
				}
				else
				{
					log.info( "Failed: " + toString() ) ;
				}
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}

	@Override
	public String toString()
	{
		String retMe = "Move \""
				+ sourceFileName
				+ "\" -> \""
				+ destinationFileName
				+ "\"";
		return retMe ;
	}

}
