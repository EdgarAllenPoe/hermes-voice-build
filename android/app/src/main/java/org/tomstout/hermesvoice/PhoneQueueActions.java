package org.tomstout.hermesvoice;
import android.content.Context;
/** Shares the upload lock: an in-flight upload may finish, then deletion rechecks state. */
final class PhoneQueueActions {
    static void discard(Context c,String id){
        Uploader.maintenance=true;
        try{synchronized(Uploader.QUEUE_LOCK){try(QueueDb db=new QueueDb(c)){db.discard(id);}}}
        finally{Uploader.maintenance=false;}
    }
    static int clearPartials(Context c)throws java.io.IOException{
        java.io.File directory=new java.io.File(c.getFilesDir(),"incoming").getCanonicalFile();java.io.File[] files=directory.listFiles();int count=0;
        if(files!=null)for(java.io.File file:files){if(!file.getCanonicalFile().getParentFile().equals(directory)||!file.isFile()||!(file.getName().endsWith(".part")||file.getName().endsWith(".bad")))continue;if(!file.delete())throw new java.io.IOException("Cannot remove partial transfer");count++;}return count;
    }
}
