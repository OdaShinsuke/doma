/*
 * Copyright 2004-2010 the Seasar Foundation and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.seasar.doma.internal.jdbc.dialect;

import java.util.ArrayList;
import java.util.List;

import org.seasar.doma.internal.jdbc.sql.node.FragmentNode;
import org.seasar.doma.internal.jdbc.sql.node.OrderByClauseNode;
import org.seasar.doma.internal.jdbc.sql.node.SelectStatementNode;
import org.seasar.doma.internal.jdbc.sql.node.WordNode;
import org.seasar.doma.jdbc.JdbcException;
import org.seasar.doma.jdbc.SqlNode;
import org.seasar.doma.message.Message;


/**
 * @author taedium
 * 
 */
public class MssqlPagingTransformer extends StandardPagingTransformer {

    public MssqlPagingTransformer(long offset, long limit) {
        super(offset, limit);
    }
    
    @Override
    public SqlNode visitSelectStatementNode(SelectStatementNode node, Void p) {
      if (processed) {
          return node;
      }
      processed = true;

      OrderByClauseNode originalOrderBy = node.getOrderByClauseNode();
      if (originalOrderBy == null) {
          throw new JdbcException(Message.DOMA2201);
      }
      
      OrderByClauseNode orderBy = new OrderByClauseNode(originalOrderBy.getWordNode());
      List<SqlNode> preNodes = new ArrayList<>();
      List<SqlNode> postNodes = new ArrayList<>();
      for (SqlNode child : originalOrderBy.getChildren()) {
          if (!postNodes.isEmpty()) {
              postNodes.add(child);
          } else if (startsPostNode(child)) {
              // pre の末尾の WhitespaceNode を post に移す
              SqlNode last = preNodes.remove(preNodes.size() - 1);
              postNodes.add(last);
              postNodes.add(child);
          } else {
              preNodes.add(child);
          }
      }
      for (SqlNode preNode : preNodes) {
          orderBy.appendNode(preNode);
      }
      
      String offset = this.offset <= 0 ? "0" : String.valueOf(this.offset);
      
      orderBy.appendNode(new FragmentNode(" offset "));
      orderBy.appendNode(new FragmentNode(offset));
      orderBy.appendNode(new FragmentNode(" rows"));
      if (this.limit > 0) {
          orderBy.appendNode(new FragmentNode(" fetch next "));
          orderBy.appendNode(new FragmentNode(String.valueOf(this.limit)));
          orderBy.appendNode(new FragmentNode(" rows only"));
      }
      
      for (SqlNode postNode : postNodes) {
          orderBy.appendNode(postNode);
      }
      
      if (node.getForUpdateClauseNode() != null) {
          orderBy.appendNode(new FragmentNode(" "));
      }
      
      SelectStatementNode result = new SelectStatementNode();
      result.setSelectClauseNode(node.getSelectClauseNode());
      result.setFromClauseNode(node.getFromClauseNode());
      result.setWhereClauseNode(node.getWhereClauseNode());
      result.setGroupByClauseNode(node.getGroupByClauseNode());
      result.setHavingClauseNode(node.getHavingClauseNode());
      result.setOrderByClauseNode(orderBy);
      result.setForUpdateClauseNode(node.getForUpdateClauseNode());
      return result;
    }
    
    /**
     * OrderByClauseNode に含まれる ソート順指定以外のノードを検出する。(FOR 句/OPTION 句)
     * @param node
     * @return ソート順以外のノードを検出したら true.それ以外は false.
     */
    protected boolean startsPostNode(SqlNode node) {
      if (!(node instanceof WordNode)) {
         return false;
      }
      String word = ((WordNode)node).getWord();
      return "for".equalsIgnoreCase(word) || 
          "option".equalsIgnoreCase(word);
    }
}