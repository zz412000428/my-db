package com.github.skyisbule.db.thread;

import com.github.skyisbule.db.executor.Selecter;
import com.github.skyisbule.db.page.SegmentPageContainer;
import com.github.skyisbule.db.result.DBResult;
import com.github.skyisbule.db.task.IoTask;
import com.github.skyisbule.db.task.SelectTask;
import com.github.skyisbule.db.type.IoTaskType;

import java.net.Socket;
import java.util.HashMap;

//定义连接客户端的监听线程，用于解析用于请求，解析sql，构造ioTask并递交给mainThread

/**
 * 处理流程为
 * 1.获取事务id，将自己注册到两个回调中心 用于接收mainThread以及IOThread的任务回调。
 * 2.解析用户的网络请求，分析sql
 * 3.根据sql生成对应的 CRUD TASK 以及IO TASK
 * 4.将CRUD TASK递交给mainThread尝试获取锁 并进入等待状态//todo  这里未来再改成等待的过程中继续视情况解析sql
 * 5.获取锁成功后mainThread会调用getLockSuccess方法 进入第二个状态
 * 6.递交ioTask给IOThread
 * 7.等待回调 将生成的结果返回给客户端
 */
public class ServerSocketThreadImp extends Thread implements ServerSocketThread{

    private Socket     socket;
    private int        transcathionId;
    private IoTask     ioTaskTemp;
    private DBResult   result;
    private DbIoThread ioThread;
    private boolean    getLock;
    private boolean    getResult;
    private boolean    isTranscation;
    private String     dbName;
    private SegmentPageContainer pageContainer;

    public void init(Socket socket,int trascathionId){
        this.socket = socket;
        this.transcathionId = trascathionId;
        this.getLock   = false;
        this.getResult = false;
    }

    /**
     * 这里需要区分是普通sql还是事务
     * 获取sql   构造对应的 crud task
     */
    //todo   未来再把这里的流程拆一拆 拆成单个函数
    public void run(){
        //先判断是否是事务

        if (isTranscation){


        }else{//不是事务  则可以简化流程 可以不用再接收socket的信息了


        }


    }

    private void doSelect(String sql){
        //这里先模拟解析到了用户的sql
        sql = "select * from test";
        String tableName = "test";
        //这里先构造 crud Task   获取最重要的两个信息：表  加锁范围  递交给mainThread
        SelectTask task = new SelectTask(transcathionId,"test",true);
        //构造完获取锁的任务后，需要通过段页表获取你需要读取的块的位置，即byte位  并基于此构造IO TASK 递交给io线程去读
        ioTaskTemp = new IoTask(transcathionId,"test",IoTaskType.READ);
        try {
            DbMainLoopThread.commit(task);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //轮训状态 一旦获取了锁就递交给io线程
        while (!getLock){
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //走到这里代表已经获取了锁并递交了io任务  开始轮训结果状态  看看结果返回没
        while(!getResult){
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //走到这里就代表结果反回了  这里将result递交给selecter 获取最终响应结果
        Selecter selecter = new Selecter();
        //拿到响应结果就可以生成串写回给用户了
        HashMap<String,Object> results = selecter.doSelect(dbName,tableName,result.data);
    }


    //拿到了IO返回的东西
    public void doIoCallBack(DBResult result) {
        //根据ioTask生成result
        this.result = result;
    }

    //代表拿到了锁，可以向IOThread提交io任务了
    public void getLockSuccess(){
        getLock = true;
        ioThread.commit(ioTaskTemp);
    }

}
