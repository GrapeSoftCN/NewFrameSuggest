package interfaceApplication;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.java.JGrapeSystem.rMsg;
import common.java.apps.appsProxy;
import common.java.database.dbFilter;
import common.java.interfaceModel.GrapeDBDescriptionModel;
import common.java.interfaceModel.GrapePermissionsModel;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.nlogger.nlogger;
import common.java.security.codec;
import common.java.session.session;
import common.java.string.StringHelper;
import common.java.time.timeHelper;

public class Suggest {
    private GrapeTreeDBModel suggest;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private String currentUser = null;
    private String pkString;

    public Suggest() {
    	
        suggest = new GrapeTreeDBModel();
        //数据模型
      	GrapeDBDescriptionModel  gDbSpecField = new GrapeDBDescriptionModel ();
        gDbSpecField.importDescription(appsProxy.tableConfig("Suggest"));
        suggest.descriptionModel(gDbSpecField);
        
        //权限模型绑定
  		GrapePermissionsModel gperm = new GrapePermissionsModel();
  		gperm.importDescription(appsProxy.tableConfig("Suggest"));
  		suggest.permissionsModel(gperm);
  		
  		pkString = suggest.getPk();

        //用户信息
        userInfo = (new session()).getDatas();
		if (userInfo != null && userInfo.size() != 0) {
			currentWeb = userInfo.getString("currentWeb"); // 当前用户所属网站id
//			userType =userInfo.getInt("userType");//当前用户身份
			currentUser = (String) userInfo.getPkValue(pkString); // 当前用户id
		}
        
        //开启权限检查
        suggest.enableCheck();
    }

    /**
     * 新增咨询建议信息
     * 
     * @param info
     *            （咨询件信息数据，内容进行base64编码）
     * @return
     *
     */
    public String AddSuggest(String info) {
        String result = rMsg.netMSG(100, "提交失败");
        if (StringHelper.InvaildString(info)) {
            return rMsg.netMSG(1, "参数异常");
        }
        info = codec.DecodeFastJSON(info);
//        info = CheckParam(info);
//        if (info.contains("errorcode")) {
//            return info;
//        }
        JSONObject object = JSONObject.toJSON(info);
        if (object != null && object.size() > 0) {
            result = add(object);
        }
        return result;
    }

    /**
     * 咨询建议回复
     * 
     * @param id
     * @param replyContent
     * @return
     */
    @SuppressWarnings("unchecked")
    public String Reply(String id, String replyContent) {
        int code = 99;
        long time = timeHelper.nowSecond(), state = 2;
        String result = rMsg.netMSG(100, "回复失败");
        if (!StringHelper.InvaildString(replyContent)) {
            JSONObject object = JSONObject.toJSON(replyContent);
            if (object != null && object.size() > 0) {
                if (object.containsKey("replyTime")) {
                    time = Long.parseLong(object.getString("replyTime"));
                }
                object.put("replyTime", time);
                if (object.containsKey("state")) {
                    state = Long.parseLong(object.getString("state"));
                    state = state != 2 ? 2 : state;
                }
                object.put("state", state);
                code = update(id, object.toString());
            }
            result = (code == 0) ? rMsg.netMSG(0, "咨询建议件回复成功") : result;
        }
        return result;
    }

    public String PageFront(String wbid, int idx, int pageSize, String CondString) {
        JSONArray array = null;
        long total = 0;
//        if (StringHelper.InvaildString(CondString)) {
        if (!StringHelper.InvaildString(CondString)) { //TODO 1
            JSONArray condArray = buildCond(CondString);
            if (condArray != null && condArray.size() > 0) {
                suggest.where(condArray);
            } else {
                return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
            }
        }
        suggest.eq("slevel", 0);
        array = suggest.dirty().field("_id,content,time,state,replyContent,replyTime").page(idx, pageSize);
        total = suggest.count();
        return rMsg.netPAGE(idx, pageSize, total,(array != null && array.size() > 0) ? decode(array) : new JSONArray());
    }

    public String Page(int idx, int pageSize) {
        return PageBy(idx, pageSize, null);
    }

    public String PageBy(int idx, int pageSize, String info) {
        long total = 0;
        JSONArray data = null;
        try {
            // 获取当前用户身份：
            // 系统管理员
            // 网站管理员
            if (StringHelper.InvaildString(currentWeb)) {
                return rMsg.netMSG(2, "当前用户信息已失效，请重新登录");
            }
            String webTree = (String) appsProxy.proxyCall("/GrapeWebInfo/WebInfo/getTree/" + currentWeb);
            if (!StringHelper.InvaildString(webTree)) {
                String[] webtree = webTree.split(",");
                suggest.or();
                for (String string : webtree) {
                    suggest.eq("wbid", string);
                }
            }
            data = suggest.dirty().desc("time").page(idx, pageSize);
            total = suggest.count();
            suggest.clear();
        } catch (Exception e) {
            nlogger.logout(e);
            data = null;
        }

        return rMsg.netPAGE(idx, pageSize, total,(data != null && data.size() > 0) ? getImg(decode(data)) : new JSONArray());
    }

    /**
     * 对已回复的咨询件进行评分
     * 
     * @param id
     * @param score
     * @return
     *
     */
    public String Score(String id, String score) {
        int code = 99;
        String result = rMsg.netMSG(100, "评分失败");
        // 验证咨询件是否存在
        JSONObject object = suggest.eq(pkString, id).eq("state", 2).find();
        if (object == null || object.size() <= 0) {
            return rMsg.netMSG(3, "待评分咨询件不存在");
        }
        if (object != null && object.size() > 0) {
            if (!score.contains("score")) {
                score = "{\"score\":" + score + "}";
            }
            code = update(id, score);
            result = code == 0 ? rMsg.netMSG(0, "评分成功") : result;
        }
        return result;
    }

    /**
     * 设置咨询件状态，支持批量操作
     * 
     * @param id
     * @return
     *
     */
    public String setSelvel(String id, String info) {
        long code = 0;
        String[] value = null;
        String result = rMsg.netMSG(100, "咨询件设置失败");
        if (!StringHelper.InvaildString(id)) {
            value = id.split(",");
        }
        if (value != null) {
            JSONObject condString = JSONObject.toJSON(info);
            if (condString != null && condString.size() > 0) {
                try {
                    suggest.or();
                    for (String _id : value) {
                        suggest.eq(pkString, _id);
                    }
                    code = suggest.data(condString).updateAll();
                } catch (Exception e) {
                    nlogger.logout(e);
                    code = 99;
                }
            }
        }
        return code > 0 ? rMsg.netMSG(0, "咨询件设置成功") : result;
    }

    /**
     * 显示某用户下的所有咨询件信息
     * 
     * @param ids
     * @param pagesize
     * @return
     */
    public String showByUser(int ids, int pagesize) {
        long total = 0;
        JSONArray array = null;
        try {
            if (StringHelper.InvaildString(currentUser)) {
                return rMsg.netMSG(3, "登录信息已失效，请重新登录");
            }
            suggest.eq("userid", currentUser);
            array = suggest.dirty().field ("_id,content,time,state,replyTime,replyContent").dirty().desc("time")
                    .page(ids, pagesize);
            total = suggest.count();
            suggest.clear();
        } catch (Exception e) {
            nlogger.logout(e);
            array = null;
        }
        return rMsg.netPAGE(ids, pagesize, total,
                (array != null && array.size() > 0) ? decode(array) : new JSONArray());
    }

    public String FindByID(String id) {
    	JSONObject object = null;
    	if(!StringHelper.InvaildString(id)){// TODO 1
    		object = suggest.eq(pkString, id).field( "_id,userid,name,consult,content,state,replyContent,score")
    				.find();
    	}
        return rMsg.netMSG(0, (object != null && object.size() > 0) ? decode(object) : new JSONObject());
    }

    /**
     * 修改操作
     * 
     * @param id
     * @param info
     * @return
     */
    private int update(String id, String info) {
        int code = 99;
        try {
            info = CheckParam(info);
            if (info.contains("errorcode")) {
                return 99;
            }
            JSONObject object = JSONObject.toJSON(info);
            if (object != null && object.size() > 0) {
                code = suggest.eq(pkString, id).data(object).update() != null ? 0 : 99;
            }
        } catch (Exception e) {
            nlogger.logout(e);
            code = 99;
        }
        return code;
    }

    /**
     * 新增操作
     * 
     * @param object
     * @return
     */
    @SuppressWarnings("unchecked")
    private String add(JSONObject object) {
        String result = rMsg.netMSG(100, "提交失败");
        // int mode = Integer.parseInt(object.get("mode").toString());
        try {
            // if (mode == 1) { // 实名
            // result = RealName(object);
            // } else {
            if (!object.containsKey("userid")) {
                object.put("userid", currentUser);
            }
            result = insert(object.toString());
            // }
            result = (result != null) ? rMsg.netMSG(0, "提交成功") : result;
        } catch (Exception e) {
            nlogger.logout(e);
            result = rMsg.netMSG(100, "提交失败");
        }
        return result;
    }

    /**
     * 插入操作
     * 
     * @param info
     * @return
     */
	private String insert(String info) {
        Object obj = null;
        JSONObject infos =JSONObject.toJSON(info);
//        JSONObject rMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 100);//设置默认查询权限
//    	JSONObject uMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 200);
//    	JSONObject dMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 300);
//    	infos.put("rMode", rMode.toJSONString()); //添加默认查看权限
//    	infos.put("uMode", uMode.toJSONString()); //添加默认修改权限
//    	infos.put("dMode", dMode.toJSONString()); //添加默认删除权限
        try {
            obj = suggest.data(infos).insertEx();
        } catch (Exception e) {
            nlogger.logout(e);
            obj = null;
        }
        return (obj != null) ? obj.toString() : null;
    }

    /**
     * 咨询建议件编码
     * 
     * @param info
     * @return
     */
    private String CheckParam(String info) {
        String temp;
        String[] value = { "content", "replyContent" };
        String result = rMsg.netMSG(1, "参数错误");
        if (!StringHelper.InvaildString(info)) {
            JSONObject object = JSONObject.toJSON(info);
            if (object != null && object.size() > 0) {
                for (String string : value) {
                    if (object.containsKey(string)) {
                        temp = object.getString(string);
                        temp = codec.DecodeHtmlTag(temp);
                        temp = codec.decodebase64(temp);
                        object.escapeHtmlPut(string, temp);
                    }
                }
                result = object.toJSONString();
            }
        }
        return result;
    }
    
    /**
     * 整合参数，将JSONObject类型的参数封装成JSONArray类型
     * 
     * @param object
     * @return
     */
    public JSONArray buildCond(String Info) {
        String key;
        Object value;
        JSONArray condArray = null;
        JSONObject object = JSONObject.toJSON(Info);
        dbFilter filter = new dbFilter();
        if (object != null && object.size() > 0) {
            for (Object object2 : object.keySet()) {
                key = object2.toString();
                value = object.get(key);
                filter.eq(key, value);
            }
            condArray = filter.build();
        } else {
            condArray = JSONArray.toJSONArray(Info);
        }
        return condArray;
    }
    
    /**
     * 对提交的咨询内容进行解码
     * 
     * @project GrapeSuggest
     * @package interfaceApplication
     * @file Suggest.java
     * 
     * @param array
     * @return
     *
     */
    @SuppressWarnings("unchecked")
    public JSONArray decode(JSONArray array) {
        JSONObject object;
        if (array == null || array.size() <= 0) {
            return array;
        }
        int l = array.size();
        for (int i = 0; i < l; i++) {
            object = (JSONObject) array.get(i);
            array.set(i, decode(object));
        }
        return array;
    }
    
    @SuppressWarnings("unchecked")
    public JSONObject decode(JSONObject object) {
        String temp;
        String[] fields = { "content", "replyContent", "reviewContent" };
        if (object == null || object.size() <= 0) {
            return new JSONObject();
        }
        for (String field : fields) {
            if (object.containsKey(field)) {
                temp = object.getString(field);
                if (!StringHelper.InvaildString(temp)) {
                    object.put(field, object.escapeHtmlGet(field));
                }
            }
        }
        return object;
    }
    
    /**
     * 获取上传的附件的详细信息
     * 
     * @param array
     * @return
     */
    public JSONArray getImg(JSONArray array) {
        JSONObject object;
        String fileInfo = "";
        String fid = "", tempid;
        if (array == null || array.size() <= 0) {
            return new JSONArray();
        }
        for (Object obj : array) {
            object = (JSONObject) obj;
            if (object.containsKey("attr")) {
                tempid = object.getString("attr");
                if (!StringHelper.InvaildString(tempid)) {
                    fid += tempid + ",";
                }
            }
        }
        if (fid.length() > 1) {
            fid = StringHelper.fixString(fid, ',');
            if (!fid.equals("")) {
                fileInfo = (String) appsProxy.proxyCall("/GrapeFile/Files/getFiles/" + fid);
            }
        }
        return FillData(array, fileInfo);
    }
    
    /**
     * 填充图片url
     * @param array
     * @param fileInfo
     * @return
     */
    @SuppressWarnings("unchecked")
    private JSONArray FillData(JSONArray array, String fileInfo) {
        JSONObject object;
        if (array != null && array.size() > 0) {
            if (!StringHelper.InvaildString(fileInfo)) {
                int l = array.size();
                for (int i = 0; i < l; i++) {
                    object = (JSONObject) array.get(i);
                    array.set(i, FillData(object, fileInfo));
                }
            }
        }
        return (array != null && array.size() > 0) ? array : new JSONArray();
    }
    
    @SuppressWarnings("unchecked")
    private JSONObject FillData(JSONObject object, String fileInfo) {
        List<String> imgList = new ArrayList<String>();
        List<String> videoList = new ArrayList<String>();
        String attr, attrlist = "", filetype = "";
        String[] attrs = null;
        JSONObject FileInfoObj;
        if (object != null && object.size() > 0) {
            if (!StringHelper.InvaildString(fileInfo)) {
                JSONObject fileObj = JSONObject.toJSON(fileInfo);
                if (fileObj != null && fileObj.size() > 0) {
                    if (object.containsKey("attr")) {
                        attr = object.getString("attr");
                        attrs = (!StringHelper.InvaildString(attr)) ? attr.split(",") : attrs;
                    }
                    if (attrs != null) {
                        for (String string : attrs) {
                            FileInfoObj  = fileObj.getJson(string);
                            attrlist = FileInfoObj.get("filepath").toString();
                            filetype = FileInfoObj.get("filetype").toString();
                            if ("1".equals(filetype)) {
                                imgList.add(attrlist);
                            }
                            if ("2".equals(filetype)) {
                                videoList.add(attrlist);
                            }
                        }
                    }
                }
            }
            object.put("image", imgList.size() > 0 ? StringHelper.join(imgList) : "");
            object.put("video", videoList.size() > 0 ? StringHelper.join(videoList) : "");
        }
        return (object != null && object.size() > 0) ? object : new JSONObject();
    }
}
