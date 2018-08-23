package com.github.skyisbule.db.thread;

import com.github.skyisbule.db.callBack.SocketToIOobserver;
import com.github.skyisbule.db.config.Config;
import com.github.skyisbule.db.io.DbRandomAccessIo;
import com.github.skyisbule.db.result.DBResult;
import com.github.skyisbule.db.task.IoTask;
import com.github.skyisbule.db.type.IoTaskType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class DbIoThread extends Thread{

    private LinkedBlockingQueue<IoTask>  queue  = new LinkedBlockingQueue<IoTask>(Config.IO_LINKED_BLOCK_QUEUE_SIZE);//io任务的阻塞队列
    //private boolean isDone = false;//run任务是否正在执行即队列是否被消费结束，true是在执行完毕，即run已退出，false是正在执行。
    private Map<String,DbRandomAccessIo> dbMap  = new HashMap<String, DbRandomAccessIo>(); //存储io的实例

    //初始化io实例
    public void init(){
        String dbRootPath = Config.DB_ROOT_PATH;
        File path = new File(dbRootPath);
        for (String files : path.list()){
            DbRandomAccessIo file = new DbRandomAccessIo(files);
            dbMap.put(files,file);
        }
    }

    /**
     * 这里的执行顺序
     * 调用阻塞io
     * 执行io任务
     * 调用回调
     * 检查是否还有任务
     * 设置任务已完成
     */
    public void run(){
        while (true){
            try {//阻塞读取io任务
                IoTask task = queue.take();
                byte[] data;
                DBResult result = DBResult.buildEmpty();
                //执行末尾插入
                if (task.type==IoTaskType.INSERT){
                    DbRandomAccessIo dbIo = dbMap.get(task.file);
                    int len = dbIo.getLen();//这个是文件的末尾哈
                    dbIo.write(len,task.data);
                    result = DBResult.buildInsert(task.getTransactionId());
                //执行内容中间的更新
                }else if (task.type==IoTaskType.UPDATE){
                    DbRandomAccessIo dbIo = dbMap.get(task.file);
                    dbIo.write(task.offset,task.data);
                    result = DBResult.buildUpdate(task.getTransactionId());
                //执行内容读取
                }else if (task.type==IoTaskType.READ || task.getType() == IoTaskType.SELECT){
                    DbRandomAccessIo dbIo = dbMap.get(task.file);
                    data = dbIo.read(task.offset,task.len);
                    result = DBResult.buildSelect(task.getTransactionId(),data);
                }
                //开始返回result对象
                SocketToIOobserver.getInstances().commit(task.getTransactionId(),result);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void commit(IoTask task){
        queue.add(task);
    }

}
