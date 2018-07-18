import React from 'react';
import { shallow } from 'enzyme';

import TopicsPage from '../TopicsPage';
import Loading from '../../common/Loading';
import { TOPICS } from '../../../constants/url';

import * as api from '../../../apis/topicsApis';

const res = { data: { uuids: { a: 'a', b: 'b' } } };

api.fetchTopics = jest.fn(() => {
  return res;
});

describe('<TopicsPage />', () => {
  let wrapper;
  beforeEach(() => {
    wrapper = shallow(<TopicsPage />);
    wrapper.setState({ isLoading: false });
  });

  it('renders <Loading />', () => {
    wrapper.setState({ isLoading: true });
    expect(wrapper.type()).toBe(Loading);
  });

  it('renders correctly', () => {
    expect(wrapper.length).toBe(1);
    expect(wrapper.props().title).toBe('Topics');
  });

  it('renders <Table />', () => {
    const table = wrapper.find('Table');
    const _props = table.props();

    // TODO: get back to the list test, the output is weird now
    expect(table.length).toBe(1);
    expect(_props.headers).toBe(wrapper.state().headers);
    expect(_props.urlDir).toBe(TOPICS);
  });
});
