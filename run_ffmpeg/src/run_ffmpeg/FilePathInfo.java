package run_ffmpeg;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

public class FilePathInfo
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
//	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_filepathinfo.txt" ;

	public FilePathInfo()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
//		common = new Common( log ) ;
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
		fileNames.add( "d:\\temp\\Test (2025)\\Season 01\\Test - S01E01 - Test Name.mkv" ) ;
		fileNames.add( "C:\\temp\\stop.txt" ) ;
		fileNames.add( "C:\\temp\\Show Name - S01E01 - Episode name.1.srt" ) ;
		fileNames.add( "C:\\temp\\Show Name - S01E01 - Episode name.en.srt" ) ;

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
				log.info( "File: " + theFile ) ;
				log.info( "theFile.length(): " + theFile.length() ) ;
				log.info( "theFile.getCanonicalPath(): " + theFile.getCanonicalPath() ) ;
				log.info( "theFile.getAbsolutePath(): " + theFile.getAbsolutePath() ) ;
				log.info( "theFile.getName(): " + theFile.getName() ) ;
				log.info( "theFile.getParent(): " + theFile.getParent() ) ;
				log.info( "theFile.getParentFile().getName(): " + theFile.getParentFile().getName() ) ;
				log.info( "theFile.getParentFile().getParent(): " + theFile.getParentFile().getParent() ) ;
				log.info( "theFile.getPath(): " + theFile.getPath() ) ;
				
				log.info( "FilenameUtils.getBaseName(): " + FilenameUtils.getBaseName( fileName ) ) ;
				log.info( "FilenameUtils.getExtension(): " + FilenameUtils.getExtension( fileName ) ) ;
				log.info( "FilenameUtils.getName(): " + FilenameUtils.getName( fileName ) ) ;
				log.info( "FilenameUtils.getPath(): " + FilenameUtils.getPath( fileName ) ) ;
				log.info( "FilenameUtils.getPrefix(): " + FilenameUtils.getPrefix( fileName ) ) ;

				Path thePath = Paths.get( fileName ) ;
				log.info( "thePath.fileName: " + fileName ) ;
				log.info( "thePath.toString: " + thePath.toString() ) ;
				log.info( "thePath.getName( 0 ): " + thePath.getName(0 ) ) ;
				log.info( "thePath.getFileName(): " + thePath.getFileName() ) ;
				log.info( "thePath.getFileSystem(): " + thePath.getFileSystem().toString() ) ;
				log.info( "thePath.getNameCount(): " + thePath.getNameCount() ) ;
				log.info( "thePath.getParent(): " + thePath.getParent() ) ;
				log.info( "thePath.getParent().getFileName(): " + thePath.getParent().getFileName() ) ;
				log.info( "thePath.getRoot(): " + thePath.getRoot() ) ;
			}

			//			List< String > allDirectories = findLowestLevelDirectories( "\\\\yoda\\MP4" ) ;
			//			log.info( "allDirectories: " + allDirectories.toString() ) ;

			File filterTestDirectoryPath = new File( "C:\\Temp" ) ;
			if( !filterTestDirectoryPath.exists() )
			{
				filterTestDirectoryPath = new File( "C:\\tmp" ) ;
			}
			log.info( "Checking for empty directories in " + filterTestDirectoryPath.getAbsolutePath() ) ;
			
			FileFilter emptyDirectoryFileFilter = new FileFilter()
			{
				public boolean accept( File dir )
				{          
					if( dir.isDirectory() && (0 == dir.list().length) )
					{
						return true ;
					}
					else
					{
						return false ;
					}
				}
			};
			final File[] filterTestDirectoryList = filterTestDirectoryPath.listFiles( emptyDirectoryFileFilter ) ;
			for( File theFile : filterTestDirectoryList )
			{
				log.info( "Found empty directory: " + theFile.getAbsolutePath() ) ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException ) ;
			theException.printStackTrace() ;
		}
	} // run()

	public List< String > findLowestLevelDirectories( final String topLevelDirectory )
	{
		log.info( "Checking tld: " + topLevelDirectory ) ;
		List< String > allDirectories = new ArrayList< String >() ;

		Set< String > dirNames = Stream.of(new File( topLevelDirectory ).listFiles())
				.filter(file -> file.isDirectory())
				.map(File::getName)
				.collect(Collectors.toSet());
		if( dirNames.isEmpty() )
		{
			// Base case -- we are at the lowest level directory.
			// Add this directory to the list of directories to return.
			allDirectories.add( topLevelDirectory ) ;
		}
		else
		{
			for( String dirName : dirNames )
			{
				//				log.info( "Calling findLowestLevelDirectories ( " + topLevelDirectory + "\\" + dirName + " )" ) ;
				allDirectories.addAll( findLowestLevelDirectories( topLevelDirectory + "\\" + dirName ) ) ;
			}
		}
		return allDirectories ;
	}

}
