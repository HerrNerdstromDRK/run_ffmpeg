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
	
	private static final String mkvInputDirectory = "\\\\yoda\\MKV_Archive5\\Movies" ;
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
		ddsf.run( mkvInputDirectory ) ;
	}
	
	public void run( final String mkvInputDirectory )
	{
		File mkvInputDirectoryFile = new File( mkvInputDirectory ) ;
		if( !mkvInputDirectoryFile.isDirectory() )
		{
			log.warning( "mkvInputDirectoryFile is not a directory: " + mkvInputDirectoryFile ) ;
			return ;
		}
		List< File > supFilesByExtension = common.getFilesInDirectoryByExtension( mkvInputDirectoryFile.getAbsolutePath(),
				supExtension ) ;
		log.info( "Found " + supFilesByExtension.size() + " .sup file(s)" ) ;
		
		int numDuplicativeSupFiles = 0 ;
		for( File supFile : supFilesByExtension )
		{
//			log.info( "supFile: " + supFile.toString() ) ;
			final String srtFileNameWithPath = supFile.getAbsolutePath().replace( "." + supExtension, "." + srtExtension ) ;
			File srtFileWithPath = new File( srtFileNameWithPath ) ;
			if( srtFileWithPath.exists() )
			{
				log.info( "Deleting .sup file: " + srtFileNameWithPath ) ;
				++numDuplicativeSupFiles ;
				
				// Delete the .sup file
				supFile.delete() ;
			}
		}
		log.info( "Deleted " + numDuplicativeSupFiles + " .sup file(s)" ) ;
	}
	
}
