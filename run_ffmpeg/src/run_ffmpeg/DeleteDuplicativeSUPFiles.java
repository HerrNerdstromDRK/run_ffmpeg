package run_ffmpeg;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * The purpoase of this class is to delete .sup files that have a corresponding .srt file.
 * @author Dan
 */
public class DeleteDuplicativeSUPFiles
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_delete_duplicative_sup_files.txt" ;
	
	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_pgs.txt" ;
	
	public String getStopFileName() {
		return stopFileName;
	}

	private static final String supExtension = "sup" ;
	private static final String srtExtension = "srt" ;
	
	public DeleteDuplicativeSUPFiles()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}
	
	public static void main( String[] args )
	{
		DeleteDuplicativeSUPFiles ddsf = new DeleteDuplicativeSUPFiles() ;
		ddsf.runAll() ;
	}

	public void runAll()
	{
		List< String > allFolders = common.getAllMKVDrivesAndFolders() ;
		log.info( "Deleting .sup files from " + allFolders.size() + " folder(s)" ) ;
		for( String folderName : allFolders )
		{
			if( common.shouldStopExecution( getStopFileName() ) )
			{
				break ;
			}
			
			runOne( folderName ) ;
		}
		log.info( "Shut down." ) ;
	}
	
	public void runOne( final String mkvInputDirectory )
	{
		File mkvInputDirectoryFile = new File( mkvInputDirectory ) ;
		if( !mkvInputDirectoryFile.isDirectory() )
		{
			log.warning( "mkvInputDirectoryFile is not a directory: " + mkvInputDirectoryFile ) ;
			return ;
		}
		
		List< File > supFilesByExtension = common.getFilesInDirectoryByExtension( mkvInputDirectory, supExtension ) ;
		log.info( mkvInputDirectory + ": Found " + supFilesByExtension.size() + " .sup file(s)" ) ;
		
		int numDuplicativeSupFiles = 0 ;
		for( File supFile : supFilesByExtension )
		{
			log.fine( "supFile: " + supFile.toString() ) ;
			final String srtFileNameWithPath = supFile.getAbsolutePath().replace( "." + supExtension, "." + srtExtension ) ;
			File srtFileWithPath = new File( srtFileNameWithPath ) ;
			if( srtFileWithPath.exists() )
			{
				log.info( "Deleting .sup file associated with srtFile: " + srtFileNameWithPath ) ;
				++numDuplicativeSupFiles ;
				
				// Delete the .sup file
				supFile.delete() ;
			}
		}
		log.info( mkvInputDirectory + ": Deleted " + numDuplicativeSupFiles + " .sup file(s)" ) ;
	}
	
}
