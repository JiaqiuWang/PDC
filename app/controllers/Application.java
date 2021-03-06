package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import utils.JsonUtil;
import utils.ModelUtil;
import utils.SHA1;
import utils.UserUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;

import db.MyDBHelper;
import db.MyDBManager;

public class Application extends Controller {

	public static Result index() {
		return ok("hello pdc");
	}

	/**
	 * user login
	 * 
	 * @return
	 */
	public static Result login() {
		JsonNode json = request().body().asJson();
		System.out.println(json.toString());
		String uid = json.findPath("id").textValue();
		String pwd = json.findPath("password").textValue();

		MyDBManager manager = new MyDBManager();

		if (manager.query("SELECT * FROM users WHERE uid=\'" + uid
				+ "\' and pwd=\'" + pwd + "\'")) {
			ObjectNode on = Json.newObject();
			on.put("result", SHA1.getSHA1String(uid));
			return ok(on);
		} else {
			return ok(JsonUtil.getFalseJson());
		}
	}

	/**
	 * register a user, and create a db for him
	 * 
	 * @return
	 */
	public static Result registerUser() {
		JsonNode json = request().body().asJson();
		System.out.println(json.toString());
		String uid = json.findPath("id").textValue();
		String pwd = json.findPath("password").textValue();

		MyDBManager manager = new MyDBManager();
		manager.insertIntoTable("users", "uid", uid, pwd,
				SHA1.getSHA1String(uid));
		// create a new db for a user
		// manager.createDB();

		MyDBHelper helper = new MyDBHelper();

		// store ontology in a new db
		OntModel model = ModelUtil.createModel(helper.getConnection());

		// create a user, store in ontology
		String prefix = model.getNsPrefixURI("");
		OntClass oUser = model.getOntClass(prefix + UserUtil.userClassname);
		Individual iUser = oUser.createIndividual(prefix + uid);
		iUser.addLabel(SHA1.getSHA1String(uid), null);
		Iterator<String> it = json.fieldNames();
		ModelUtil.addIndividualProperties(oUser, iUser, it, json);
		return ok(JsonUtil.getTrueJson());
	}

	/**
	 * register a app
	 * 
	 * @return
	 */
	public static Result registerApp() {
		JsonNode json = request().body().asJson();
		System.out.println(json.toString());
		String sname = json.findPath("name").textValue();
		String packagename = json.findPath("packagename").textValue();

		MyDBManager manager = new MyDBManager();
		manager.insertIntoServiceTable(sname, packagename);

		return ok(JsonUtil.getTrueJson());
	}

	/**
	 * set privacy rules
	 * 
	 * @return
	 */
	public static Result setRules() {
		JsonNode json = request().body().asJson();
		System.out.println(json.toString());

		String uid = json.findPath("uid").textValue();
		MyDBManager manager = new MyDBManager();

		// confirm whether user is correct
		if (!manager.confirmUser(uid)) {
			return ok(JsonUtil.getFalseJson());
		}

		String classname = json.findPath("classname").textValue();
		Boolean allpro = json.findPath("allpro").asBoolean();
		Integer level = json.findPath("level").asInt();
		JsonNode array = json.findPath("pro");
		ArrayList<String> proList = new ArrayList<String>();

		// if set some properties
		if (!allpro) {
			if (array.isArray()) {
				for (Iterator<JsonNode> it = array.elements(); it.hasNext();) {
					proList.add(it.next().textValue());
				}
			}
		}

		// get sid and fid
		ArrayList<String> gsidList = new ArrayList<String>();
		ArrayList<String> gfidList = new ArrayList<String>();
		if (level == 1) {
			JsonNode sarray = json.findPath("sid");
			if (sarray.isArray()) {
				for (Iterator<JsonNode> it = sarray.elements(); it.hasNext();) {
					gsidList.add(it.next().textValue());
				}
			}
		}

		if (level == 2) {
			JsonNode farray = json.findPath("fid");
			if (farray.isArray()) {
				for (Iterator<JsonNode> it = farray.elements(); it.hasNext();) {
					gfidList.add(it.next().textValue());
				}
			}
		}

		// if the rule is existed, modify the properties
		if (manager
				.query("SELECT * FROM rules WHERE uid=\'" + uid
						+ "\' and classname=\'" + classname + "\' and level = "
						+ level)) {
			if (level == 0) {

				manager.update(allpro,
						proList.toArray(new String[proList.size()]), level,
						classname, uid);
			} else if (level == 1) {

				ArrayList<String> ridList = manager
						.getList("SELECT rid FROM rules WHERE uid = \'" + uid
								+ "\' and classname = \'" + classname
								+ "\' and level = " + level);
				HashMap<String, String> sidMap = new HashMap<String, String>();
				for (String rid : ridList) {
					ArrayList<String> tmp = manager
							.getList("SELECT sid FROM rules_services where rid = \'"
									+ rid + "\'");
					HashMap<String, String> tmpMap = new HashMap<String, String>();
					for (String tsid : tmp) {
						tmpMap.put(tsid, rid);
					}
					sidMap.putAll(tmpMap);
				}

				// delete existed rules
				for (String es : gsidList) {
					if (sidMap.containsKey(es)) {
						manager.delete("DELETE FROM rules_services WHERE sid = \'"
								+ es
								+ "\' and rid = \'"
								+ sidMap.get(es)
								+ "\'");
					}
				}

				// add new rules into table rules
				String irid = manager.insertIntoRules(uid, classname, allpro,
						proList.toArray(new String[proList.size()]), level);

				// add new rules into table rules_friends
				for (String sid : gsidList) {
					manager.insertIntoRulesServices(irid, sid);
				}

			} else if (level == 2) {

				ArrayList<String> ridList = manager
						.getList("SELECT rid FROM rules WHERE uid = \'" + uid
								+ "\' and classname = \'" + classname
								+ "\' and level = " + level);
				HashMap<String, String> fidMap = new HashMap<String, String>();
				for (String rid : ridList) {
					ArrayList<String> tmp = manager
							.getList("SELECT fid FROM rules_friends where rid = \'"
									+ rid + "\'");
					HashMap<String, String> tmpMap = new HashMap<String, String>();
					for (String tfid : tmp) {
						tmpMap.put(tfid, rid);
					}
					fidMap.putAll(tmpMap);
				}

				// delete existed rules
				for (String ef : gfidList) {
					if (fidMap.containsKey(ef)) {
						manager.delete("DELETE FROM rules_friends WHERE fid = \'"
								+ ef
								+ "\' and rid = \'"
								+ fidMap.get(ef)
								+ "\'");
					}
				}

				// add new rules into table rules
				String irid = manager.insertIntoRules(uid, classname, allpro,
						proList.toArray(new String[proList.size()]), level);

				System.out.println("max id " + irid);

				// add new rules into table rules_friends
				for (String fid : gfidList) {
					manager.insertIntoRulesFriends(irid, fid);
				}
			}
		} else {
			manager.insertIntoRules(uid, classname, allpro,
					proList.toArray(new String[proList.size()]), level);
		}

		return ok(JsonUtil.getTrueJson());
	}
}
