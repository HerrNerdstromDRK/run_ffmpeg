package run_ffmpeg;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class FindFilesWorkerThread extends run_ffmpegWorkerThread
{
	private transient FindFiles theController = null ;
	private String folderToSearch = null ;
	private List< String > extensionsToFind = null ;
	
	public FindFilesWorkerThread( FindFiles theController,
			Logger log,
			Common common,
			final String folderToSearch,
			final List< String > extensionsToFind )
	{
		super( log, common ) ;
		
		assert( theController != null ) ;
		assert( folderToSearch != null ) ;
		assert( extensionsToFind != null ) ;
		
		this.theController = theController ;
		this.folderToSearch = folderToSearch ;
		this.extensionsToFind = extensionsToFind ;
	}
	
	@Override
	public void run()
	{
		List< File > foundFiles = common.getFilesInDirectoryByExtension( folderToSearch, extensionsToFind.toArray( new String[ 0 ] ) ) ;
		theController.addFoundFiles( foundFiles ) ;
	}
	
	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
