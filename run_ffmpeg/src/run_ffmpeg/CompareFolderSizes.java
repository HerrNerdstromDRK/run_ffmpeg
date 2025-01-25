package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class checks the subfolders from two input folders for the size of each. The intent
 * is to identify folders of differing sizes to verify copy success.
 */
public class CompareFolderSizes 
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_compare_folder_sizes.txt" ;

	public CompareFolderSizes()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		final String inputFolder1 = new String( "\\\\skywalker\\Media\\MKV\\MKV_Archive10\\Movies" ) ;
		final String inputFolder2 = new String( "\\\\yoda\\MKV_Archive10\\Movies" ) ;

		CompareFolderSizes cfs = new CompareFolderSizes() ;
		cfs.run( inputFolder1, inputFolder2 ) ;
	}

	public void run( final String inputFolder1, final String inputFolder2 )
	{
		try
		{
			final File inputFolder1File = new File( inputFolder1 ) ;
			final File inputFolder2File = new File( inputFolder2 ) ;

			assert( inputFolder1File.isDirectory() ) ;
			assert( inputFolder2File.isDirectory() ) ;

			// Get all subfolders in each folder and load into a map.
			List< File > subFolders1 = common.getSubDirectories( inputFolder1File ) ;
			List< File > subFolders2 = common.getSubDirectories( inputFolder2File ) ;

			log.info( "subFolders1: " + subFolders1.toString() ) ;
			log.info( "subFolders2: " + subFolders2.toString() ) ;

			Map< String, File > subFolders1Map = new HashMap< String, File >() ;
			Map< String, File > subFolders2Map = new HashMap< String, File >() ;

			for( File theFile : subFolders1 )
			{
				subFolders1Map.put( theFile.getName(), theFile ) ;
			}
			for( File theFile : subFolders2 )
			{
				subFolders2Map.put( theFile.getName(),  theFile ) ;
			}
			log.info( "subFolders1Map: " + mapToString( subFolders1Map ) ) ;
			log.info( "subFolders2Map: " + mapToString( subFolders2Map ) ) ;

			// Walk through each directory in the first map and lookup and compare its size
			// to that in the second map
			for( Map.Entry< String, File > entry : subFolders1Map.entrySet() )
			{
				final String dir1Name = entry.getKey() ;
				final File dir1File = entry.getValue() ;

				final long folder1Size = getFolderSize( dir1File ) ;
				log.fine( "dirName: " + dir1Name + ", total size of all files: " + folder1Size ) ;
				
				// Now lookup the same directory in folder 2
				final File dir2File = subFolders2Map.get( dir1Name ) ;
				if( null == dir2File )
				{
					log.warning( "Unable to find " + dir1Name + " in folder 2" ) ;
				}
				final long folder2Size = getFolderSize( dir2File ) ;
				if( folder1Size == folder2Size )
				{
					log.fine( "MATCH for file " + dir1Name ) ;
				}
				else
				{
					log.info( "NO MATCH for file " + dir1Name ) ;
				}
			}

		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}

	/**
	 * Return the total length of all files under the current directory.
	 * @param theDir
	 * @return
	 */
	public long getFolderSize( final File theDir )
	{
		long retMe = 0 ;

		try
		{
			Stream< Path > walk = Files.walk( Paths.get( theDir.getAbsolutePath() ) ) ;
			List< String > fileNames = walk.filter( Files::isRegularFile ).map( x -> x.toString() ).collect( Collectors.toList() ) ;
			walk.close() ;
			
			for( String fileName : fileNames )
			{
				// fileName is the full path
//				log.info( "fileName: " + fileName ) ;
				
				File theFile = new File( fileName ) ;
				retMe += theFile.length() ;
			}
			
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
		return retMe ;
	}

	public String mapToString( Map< String, File > theMap )
	{
		String retMe = "{" ;
		for( Map.Entry< String, File > entry : theMap.entrySet() )
		{
			if( retMe.length() != 1 )
			{
				retMe += "," ;
			}
			retMe += entry.getKey()
					+ entry.getValue().length() ;
		}
		retMe += "}" ;

		return retMe ;
	}

}
